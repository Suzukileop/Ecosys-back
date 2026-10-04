package com.plateforme.ecosystem.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Time-limited signatures for private objects served by {@code /api/storage}.
 *
 * <p>On R2 a private object is read through a presigned S3 URL, which expires on its own. The
 * local storage service has no such mechanism, so {@code /api/storage/**} is open to anyone with
 * the URL — and the route has to stay unauthenticated, because an {@code <img>} or {@code <video>}
 * never sends the bearer token. This gives the local path the same guarantee by a different
 * route: a private key is only served with a signature the server issued, and only until it
 * expires.
 *
 * <p>The signature covers the object key and the expiry, nothing else. Display parameters
 * ({@code w}, {@code disposition}, {@code filename}) are deliberately left out: they change how
 * the object is rendered, never which object is returned, so letting a reader vary them costs
 * nothing and keeps one signed URL usable at several widths.
 */
@Component
@Slf4j
public class StorageSignature {

    public static final String EXPIRY_PARAM = "exp";
    public static final String SIGNATURE_PARAM = "sig";

    private static final String ALGORITHM = "HmacSHA256";

    @Value("${app.storage.signing-secret:}")
    private String configuredSecret;

    private byte[] secret;

    @PostConstruct
    void resolveSecret() {
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            secret = configuredSecret.trim().getBytes(StandardCharsets.UTF_8);
            return;
        }
        /*
         * No secret configured: mint one per boot. Signed URLs are short-lived anyway, so the only
         * consequence is that links issued before a restart stop working — which is the safe
         * failure. A deployment running more than one instance must set app.storage.signing-secret
         * so they agree.
         */
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        secret = random;
        log.info("app.storage.signing-secret is unset — using a per-boot key for private media URLs");
    }

    /**
     * Whether {@code objectKey} may only be served with a valid signature. Any key with a
     * {@code private} path segment qualifies, so a new private prefix is covered the day it is
     * added rather than the day someone remembers to list it here.
     */
    public static boolean isPrivateKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return false;
        }
        String normalized = "/" + objectKey.replace('\\', '/') + "/";
        return normalized.contains("/private/");
    }

    /** Epoch-second expiry for a URL valid for {@code expiryMinutes} from now. */
    public long expiryFromNow(int expiryMinutes) {
        int minutes = expiryMinutes > 0 ? expiryMinutes : 15;
        return Instant.now().plusSeconds(minutes * 60L).getEpochSecond();
    }

    public String sign(String objectKey, long expiresAtEpochSecond) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            mac.update(objectKey.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) ':');
            mac.update(Long.toString(expiresAtEpochSecond).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign storage URL", ex);
        }
    }

    /** True only for a signature this server issued for this key, and not yet expired. */
    public boolean isValid(String objectKey, String expiry, String signature) {
        if (objectKey == null || expiry == null || signature == null) {
            return false;
        }
        long expiresAt;
        try {
            expiresAt = Long.parseLong(expiry.trim());
        } catch (NumberFormatException ex) {
            return false;
        }
        if (Instant.now().getEpochSecond() > expiresAt) {
            return false;
        }
        byte[] expected = sign(objectKey, expiresAt).getBytes(StandardCharsets.UTF_8);
        byte[] provided = signature.trim().getBytes(StandardCharsets.UTF_8);
        /* Constant-time: a length-or-prefix comparison leaks the signature one byte at a time. */
        return MessageDigest.isEqual(expected, provided);
    }
}
