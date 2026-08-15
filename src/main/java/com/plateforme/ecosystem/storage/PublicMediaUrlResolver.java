package com.plateforme.ecosystem.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Normalise les URLs publiques stockées en base (chemins relatifs, anciennes bases, clés objet)
 * et vérifie en local que le fichier existe avant de l'exposer à l'API.
 */
@Service
@Slf4j
public class PublicMediaUrlResolver {

    private static final String STORAGE_PATH_PREFIX = "/api/storage/";

    @Value("${app.storage.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    @Value("${app.storage.local-dir:./build/storage-uploads}")
    private String localDir;

    @Value("${app.r2.enabled:false}")
    private boolean r2Enabled;

    public String resolveAvatarUrl(String raw) {
        return resolvePublicUrl(raw);
    }

    public String resolvePublicUrl(String raw) {
        String cleaned = blankToNull(raw);
        if (cleaned == null) {
            return null;
        }

        String objectKey = extractStorageObjectKey(cleaned);
        if (objectKey == null) {
            if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
                return cleaned;
            }
            return null;
        }

        if (!r2Enabled && !localFileExists(objectKey)) {
            log.debug("Public media missing locally key={}", objectKey);
            return null;
        }

        return buildPublicUrl(objectKey);
    }

    private String extractStorageObjectKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();

        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://") && !trimmed.startsWith("/")) {
            if (looksLikeObjectKey(trimmed)) {
                return trimmed;
            }
        }

        String path = trimmed;
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            try {
                path = URI.create(trimmed).getPath();
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }

        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        int storageIdx = path.indexOf(STORAGE_PATH_PREFIX);
        if (storageIdx < 0) {
            return null;
        }

        String encodedKey = path.substring(storageIdx + STORAGE_PATH_PREFIX.length());
        if (encodedKey.isBlank()) {
            return null;
        }
        return decodeObjectKey(encodedKey);
    }

    private static boolean looksLikeObjectKey(String value) {
        return value.startsWith("profiles/public/")
                || value.startsWith("content/public/")
                || value.startsWith("marketplace/public/")
                || value.startsWith("demos/");
    }

    private boolean localFileExists(String objectKey) {
        try {
            Path storageRoot = Path.of(localDir).toAbsolutePath().normalize();
            Path target = storageRoot.resolve(objectKey).normalize();
            if (!target.startsWith(storageRoot)) {
                return false;
            }
            return Files.exists(target) && Files.isRegularFile(target);
        } catch (RuntimeException ex) {
            log.warn("Unable to verify local media key={}: {}", objectKey, ex.getMessage());
            return false;
        }
    }

    private String buildPublicUrl(String objectKey) {
        String base = publicBaseUrl == null ? "http://localhost:8080" : publicBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return UriComponentsBuilder.fromUriString(base)
                .path(STORAGE_PATH_PREFIX)
                .path(objectKey)
                .toUriString();
    }

    private static String decodeObjectKey(String raw) {
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return raw;
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
