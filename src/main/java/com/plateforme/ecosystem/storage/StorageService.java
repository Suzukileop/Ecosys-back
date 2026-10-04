package com.plateforme.ecosystem.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public interface StorageService {

    /**
     * Stockage public simulé ou R2 ; retourne une URL utilisable côté client.
     */
    String uploadPublicFile(String objectKey, InputStream inputStream, long contentLength, String contentType)
            throws IOException;

    /**
     * Upload multipart avec validation type/taille selon le chemin (vidéo ou image).
     */
    String uploadFile(MultipartFile file, String objectKey) throws IOException;

    /**
     * Private storage (marketplace main files) — returns object key only, never a public URL.
     */
    String uploadPrivateFile(String objectKey, InputStream inputStream, long contentLength, String contentType)
            throws IOException;

    /**
     * Private multipart upload — returns object key only.
     */
    String uploadPrivateFile(MultipartFile file, String objectKey) throws IOException;

    void deleteFile(String objectKey) throws IOException;

    /**
     * Deletes every object whose key starts with {@code prefix} (e.g. {@code profiles/public/<userId>/}).
     * The prefix must end with "/" so a sibling folder sharing the same leading characters is never matched.
     *
     * @return number of objects deleted
     */
    int deleteByPrefix(String prefix) throws IOException;

    /**
     * URL de lecture / streaming (presignée sur R2, URL publique en local).
     */
    String generateSignedUrl(String objectKey, int expiryMinutes) throws IOException;

    /**
     * URL de téléchargement forcé (Content-Disposition: attachment sur R2, paramètres en local).
     */
    String generateSignedDownloadUrl(String objectKey, String downloadFilename, int expiryMinutes)
            throws IOException;

    /**
     * Stores an image as a canonical file plus the fixed-width derivatives of
     * {@link ImageRenditions}, so a reader can ask for the width it displays instead of pulling
     * the full-size original. Non-image payloads are stored untouched.
     *
     * <p>A derivative that fails to store is logged and skipped, never fatal: the canonical file
     * alone keeps the upload usable, and {@code StorageController} rebuilds the missing width on
     * first request.
     */
    default String uploadPublicImage(String objectKey, byte[] payload, String contentType) throws IOException {
        var renditions = ImageRenditions.build(payload, contentType);
        if (renditions.isEmpty()) {
            return uploadPublicFile(objectKey, new ByteArrayInputStream(payload), payload.length, contentType);
        }
        var set = renditions.get();
        String key = ImageRenditions.canonicalKey(objectKey, contentType, set.canonicalContentType());
        String url = uploadPublicFile(
                key,
                new ByteArrayInputStream(set.canonicalBytes()),
                set.canonicalBytes().length,
                set.canonicalContentType());
        for (var derivative : set.derivatives()) {
            String derivativeKey = ImageRenditions.derivativeKey(key, derivative.width(), derivative.contentType());
            try {
                uploadPublicFile(
                        derivativeKey,
                        new ByteArrayInputStream(derivative.bytes()),
                        derivative.bytes().length,
                        derivative.contentType());
            } catch (IOException | RuntimeException ex) {
                StorageLog.LOG.warn("Derivative not stored key={}: {}", derivativeKey, ex.toString());
            }
        }
        return url;
    }

    /** {@link #uploadPublicImage} for private objects; returns the object key, never a URL. */
    default String uploadPrivateImage(String objectKey, byte[] payload, String contentType) throws IOException {
        var renditions = ImageRenditions.build(payload, contentType);
        if (renditions.isEmpty()) {
            return uploadPrivateFile(objectKey, new ByteArrayInputStream(payload), payload.length, contentType);
        }
        var set = renditions.get();
        String canonicalKey = ImageRenditions.canonicalKey(objectKey, contentType, set.canonicalContentType());
        String key = uploadPrivateFile(
                canonicalKey,
                new ByteArrayInputStream(set.canonicalBytes()),
                set.canonicalBytes().length,
                set.canonicalContentType());
        for (var derivative : set.derivatives()) {
            String derivativeKey =
                    ImageRenditions.derivativeKey(canonicalKey, derivative.width(), derivative.contentType());
            try {
                uploadPrivateFile(
                        derivativeKey,
                        new ByteArrayInputStream(derivative.bytes()),
                        derivative.bytes().length,
                        derivative.contentType());
            } catch (IOException | RuntimeException ex) {
                StorageLog.LOG.warn("Private derivative not stored key={}: {}", derivativeKey, ex.toString());
            }
        }
        return key;
    }
}
