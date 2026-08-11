package com.plateforme.ecosystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cloudflare R2 (S3-compatible). Quand {@code enabled=true}, {@link com.plateforme.ecosystem.storage.R2StorageService}
 * remplace le stockage local.
 */
@ConfigurationProperties(prefix = "app.r2")
public record R2StorageProperties(
        boolean enabled,
        String bucket,
        String endpoint,
        String accessKey,
        String secretKey,
        /**
         * URL publique de base (ex. domaine custom ou {@code https://pub-….r2.dev}) sans slash final.
         */
        String publicBaseUrl
) {

    public R2StorageProperties {
        bucket = blankToEmpty(bucket);
        endpoint = blankToEmpty(endpoint);
        accessKey = blankToEmpty(accessKey);
        secretKey = blankToEmpty(secretKey);
        publicBaseUrl = blankToEmpty(publicBaseUrl);
    }

    private static String blankToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * Endpoint S3 pour le client : sans slash final, et sans suffixe {@code /{bucket}} si collé par erreur depuis le dashboard.
     */
    public String resolvedS3Endpoint() {
        if (endpoint.isEmpty()) {
            return "";
        }
        String e = endpoint.replaceAll("/+$", "");
        if (!bucket.isEmpty() && e.endsWith("/" + bucket)) {
            return e.substring(0, e.length() - ("/" + bucket).length()).replaceAll("/+$", "");
        }
        return e;
    }
}
