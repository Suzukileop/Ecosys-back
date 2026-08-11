package com.plateforme.ecosystem.storage;

import org.springframework.web.multipart.MultipartFile;

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
     * URL de lecture / streaming (presignée sur R2, URL publique en local).
     */
    String generateSignedUrl(String objectKey, int expiryMinutes) throws IOException;

    /**
     * URL de téléchargement forcé (Content-Disposition: attachment sur R2, paramètres en local).
     */
    String generateSignedDownloadUrl(String objectKey, String downloadFilename, int expiryMinutes)
            throws IOException;
}
