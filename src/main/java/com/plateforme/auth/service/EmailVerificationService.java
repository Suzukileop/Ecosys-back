package com.plateforme.auth.service;

import com.plateforme.shared.exception.BusinessException;
import com.plateforme.shared.mail.MailDeliveryService;
import com.plateforme.shared.util.LogMasking;
import com.plateforme.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * One-time e-mail verification codes for local sign-ups.
 *
 * <ul>
 *   <li>6 digits from {@link SecureRandom}, valid {@value #CODE_TTL_MINUTES} minutes;</li>
 *   <li>only an HMAC of the code is stored (Redis), compared in constant time;</li>
 *   <li>{@value #MAX_ATTEMPTS} wrong guesses burn the code — a new one must be requested;</li>
 *   <li>a new code at most every {@value #RESEND_COOLDOWN_SECONDS}s and {@value #MAX_SENDS_PER_HOUR} per hour,
 *       so the endpoint can't be used to flood someone's inbox.</li>
 * </ul>
 * A new code replaces the previous one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    public static final int CODE_TTL_MINUTES = 15;
    public static final int RESEND_COOLDOWN_SECONDS = 60;
    static final int MAX_ATTEMPTS = 5;
    static final int MAX_SENDS_PER_HOUR = 5;

    private static final String CODE_KEY = "email-verify:code:";
    private static final String COOLDOWN_KEY = "email-verify:cooldown:";
    private static final String SENDS_KEY = "email-verify:sends:";
    private static final String FIELD_HASH = "hash";
    private static final String FIELD_ATTEMPTS = "attempts";

    private final RedisTemplate<String, String> redisTemplate;
    private final MailDeliveryService mailDeliveryService;
    private final SecureRandom random = new SecureRandom();

    /** Server-side key for the code HMAC, so a Redis dump alone can't be brute-forced offline. */
    @Value("${app.jwt.secret}")
    private String hmacKey;

    /**
     * Sends a fresh code unless one was sent less than {@value #RESEND_COOLDOWN_SECONDS}s ago.
     *
     * @return seconds before another code can be requested
     * @throws BusinessException {@code TOO_MANY_VERIFICATION_EMAILS} past the hourly cap
     */
    public long sendCode(User user, String locale) {
        String id = user.getId().toString();
        Boolean free = redisTemplate.opsForValue()
                .setIfAbsent(COOLDOWN_KEY + id, "1", Duration.ofSeconds(RESEND_COOLDOWN_SECONDS));
        if (!Boolean.TRUE.equals(free)) {
            return cooldownRemainingSeconds(user);
        }

        Long sends = redisTemplate.opsForValue().increment(SENDS_KEY + id);
        if (sends != null && sends == 1) {
            redisTemplate.expire(SENDS_KEY + id, Duration.ofHours(1));
        }
        if (sends != null && sends > MAX_SENDS_PER_HOUR) {
            throw new BusinessException(
                    "TOO_MANY_VERIFICATION_EMAILS",
                    "You've requested too many codes. Please try again later.");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        String key = CODE_KEY + id;
        redisTemplate.delete(key);
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        hash.putAll(key, Map.of(FIELD_HASH, hmac(id, code), FIELD_ATTEMPTS, "0"));
        redisTemplate.expire(key, Duration.ofMinutes(CODE_TTL_MINUTES));

        VerificationEmailTemplates.Email email =
                VerificationEmailTemplates.render(locale, user.getFullName(), code, CODE_TTL_MINUTES);
        mailDeliveryService.sendPlainText(user.getEmail(), email.subject(), email.body());
        log.info("Code de vérification envoyé à {}", LogMasking.email(user.getEmail()));
        return RESEND_COOLDOWN_SECONDS;
    }

    /** Like {@link #sendCode} but never throws on the hourly cap — for flows where the code is a bonus. */
    public void sendCodeQuietly(User user, String locale) {
        try {
            sendCode(user, locale);
        } catch (BusinessException ex) {
            log.info("Code de vérification non renvoyé à {}: {}", LogMasking.email(user.getEmail()), ex.getCode());
        }
    }

    /**
     * Checks a submitted code; on success the code is consumed.
     *
     * @throws BusinessException {@code VERIFICATION_CODE_EXPIRED}, {@code VERIFICATION_TOO_MANY_ATTEMPTS}
     *                           or {@code INVALID_VERIFICATION_CODE}
     */
    public void verify(User user, String code) {
        String id = user.getId().toString();
        String key = CODE_KEY + id;
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        String stored = hash.get(key, FIELD_HASH);
        if (stored == null) {
            throw new BusinessException("VERIFICATION_CODE_EXPIRED", "This code has expired. Request a new one.");
        }

        Long attempts = hash.increment(key, FIELD_ATTEMPTS, 1);
        if (attempts != null && attempts > MAX_ATTEMPTS) {
            redisTemplate.delete(key);
            throw new BusinessException(
                    "VERIFICATION_TOO_MANY_ATTEMPTS",
                    "Too many incorrect attempts. Request a new code.");
        }

        String submitted = code == null ? "" : code.trim();
        boolean matches = MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8),
                hmac(id, submitted).getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw new BusinessException("INVALID_VERIFICATION_CODE", "This code is incorrect. Check it and try again.");
        }

        redisTemplate.delete(key);
        redisTemplate.delete(COOLDOWN_KEY + id);
    }

    public long cooldownRemainingSeconds(User user) {
        Long ttl = redisTemplate.getExpire(COOLDOWN_KEY + user.getId(), TimeUnit.SECONDS);
        return ttl == null || ttl < 0 ? 0 : ttl;
    }

    private String hmac(String userId, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((userId + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
