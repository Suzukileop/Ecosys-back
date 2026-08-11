package com.plateforme.ecosystem.storage;

import com.plateforme.shared.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

@Service
@Slf4j
@ConditionalOnProperty(prefix = "app.r2", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LocalDevStorageService implements StorageService {

    private static final long MAX_VIDEO_BYTES = 500L * 1024 * 1024;
    private static final long MAX_IMAGE_BYTES = 30L * 1024 * 1024;
    private static final long MAX_THUMBNAIL_VIDEO_BYTES = 25L * 1024 * 1024;
    private static final long MAX_CONTENT_PDF_BYTES = 50L * 1024 * 1024;

    private static final Set<String> VIDEO_TYPES = Set.of("video/mp4", "video/quicktime", "video/webm");
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> PDF_TYPES = Set.of("application/pdf");
    private static final Set<String> AUDIO_TYPES = Set.of(
            "audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav", "audio/aac",
            "audio/x-aac", "audio/mp4", "audio/ogg", "audio/vorbis");

    @Value("${app.storage.local-dir:./build/storage-uploads}")
    private String localDir;

    @Value("${app.storage.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    @Override
    public String uploadPublicFile(String objectKey, InputStream inputStream, long contentLength, String contentType)
            throws IOException {
        Path target = resolveTarget(objectKey);
        Files.createDirectories(target.getParent());
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        String url = buildPublicUrl(objectKey);
        log.info("Fichier stocké localement clé={} type={} -> {}", objectKey, contentType, url);
        return url;
    }

    @Override
    public String uploadFile(MultipartFile file, String objectKey) throws IOException {
        validateMultipart(file, objectKey);
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        return uploadPublicFile(objectKey, file.getInputStream(), file.getSize(), contentType);
    }

    @Override
    public String uploadPrivateFile(String objectKey, InputStream inputStream, long contentLength, String contentType)
            throws IOException {
        Path target = resolveTarget(objectKey);
        Files.createDirectories(target.getParent());
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        log.info("Private file stored locally key={} type={}", objectKey, contentType);
        return objectKey;
    }

    @Override
    public String uploadPrivateFile(MultipartFile file, String objectKey) throws IOException {
        validateMarketplacePrivateFile(file);
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        return uploadPrivateFile(objectKey, file.getInputStream(), file.getSize(), contentType);
    }

    @Override
    public void deleteFile(String objectKey) throws IOException {
        Path target = resolveTarget(objectKey);
        Files.deleteIfExists(target);
        log.info("Fichier local supprimé clé={}", objectKey);
    }

    @Override
    public String generateSignedUrl(String objectKey, int expiryMinutes) throws IOException {
        return buildPublicUrl(objectKey);
    }

    @Override
    public String generateSignedDownloadUrl(String objectKey, String downloadFilename, int expiryMinutes)
            throws IOException {
        return UriComponentsBuilder.fromUriString(buildPublicUrl(objectKey))
                .queryParam("disposition", "attachment")
                .queryParam("filename", downloadFilename != null && !downloadFilename.isBlank()
                        ? downloadFilename
                        : "download")
                .build()
                .toUriString();
    }

    private Path resolveTarget(String objectKey) throws IOException {
        Path storageRoot = Path.of(localDir).toAbsolutePath().normalize();
        Path target = storageRoot.resolve(objectKey).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new IOException("Chemin de fichier invalide");
        }
        return target;
    }

    private String buildPublicUrl(String objectKey) {
        String publicBase = publicBaseUrl == null ? "http://localhost:8080" : publicBaseUrl.trim();
        while (publicBase.endsWith("/")) {
            publicBase = publicBase.substring(0, publicBase.length() - 1);
        }
        return UriComponentsBuilder.fromUriString(publicBase)
                .path("/api/storage/")
                .path(objectKey)
                .toUriString();
    }

    private void validateMultipart(MultipartFile file, String objectKey) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("FILE_REQUIRED", "Fichier requis");
        }
        if (objectKey != null && objectKey.startsWith("marketplace/public/")) {
            validateMarketplaceThumbnail(file);
            return;
        }
        if (objectKey != null && objectKey.startsWith("content/public/")) {
            if (objectKey.contains("/thumbnails/")) {
                validateProfileImage(file);
            } else {
                validateContentMedia(file);
            }
            return;
        }
        if (objectKey != null && objectKey.startsWith("profiles/public/")) {
            validateProfileImage(file);
            return;
        }
        String ct = file.getContentType() != null ? file.getContentType() : "";
        boolean videoPath = objectKey != null && (objectKey.startsWith("models/")
                || objectKey.endsWith(".mp4") || objectKey.endsWith(".mov") || objectKey.endsWith(".webm"));
        boolean audioPath = AUDIO_TYPES.contains(ct)
                || (objectKey != null && (objectKey.endsWith(".mp3") || objectKey.endsWith(".wav")
                || objectKey.endsWith(".aac") || objectKey.endsWith(".ogg")));
        if (videoPath) {
            if (!VIDEO_TYPES.contains(ct)) {
                throw new BusinessException("INVALID_FILE_TYPE",
                        "Types vidéo acceptés : video/mp4, video/quicktime, video/webm");
            }
            if (file.getSize() > MAX_VIDEO_BYTES) {
                throw new BusinessException("FILE_TOO_LARGE", "Taille maximale vidéo : 500 Mo");
            }
        } else if (audioPath) {
            if (file.getSize() > 50L * 1024 * 1024) {
                throw new BusinessException("FILE_TOO_LARGE", "Taille maximale audio : 50 Mo");
            }
        } else {
            if (!IMAGE_TYPES.contains(ct)) {
                throw new BusinessException("INVALID_FILE_TYPE",
                        "Types image acceptés : image/jpeg, image/png, image/webp");
            }
            if (file.getSize() > MAX_IMAGE_BYTES) {
                throw new BusinessException("FILE_TOO_LARGE", "Taille maximale image : 30 Mo");
            }
        }
    }

    private void validateMarketplacePrivateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("FILE_REQUIRED", "File is required");
        }
        long maxBytes = 500L * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessException("FILE_TOO_LARGE", "Maximum marketplace file size: 500 MB");
        }
    }

    private void validateMarketplaceThumbnail(MultipartFile file) {
        String ct = file.getContentType() != null ? file.getContentType() : "";
        boolean isImage = IMAGE_TYPES.contains(ct);
        boolean isVideo = VIDEO_TYPES.contains(ct);
        if (!isImage && !isVideo) {
            throw new BusinessException("INVALID_FILE_TYPE",
                    "Miniature : image (jpeg, png, webp) ou vidéo (mp4, webm, mov)");
        }
        if (isImage && file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException("FILE_TOO_LARGE", "Taille maximale image : 30 Mo");
        }
        if (isVideo && file.getSize() > MAX_THUMBNAIL_VIDEO_BYTES) {
            throw new BusinessException("FILE_TOO_LARGE", "Vidéo miniature : max 25 Mo");
        }
    }

    private void validateProfileImage(MultipartFile file) {
        String ct = file.getContentType() != null ? file.getContentType() : "";
        if (!IMAGE_TYPES.contains(ct)) {
            throw new BusinessException("INVALID_FILE_TYPE",
                    "Photo de profil : image/jpeg, image/png ou image/webp");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException("FILE_TOO_LARGE", "Taille maximale image : 30 Mo");
        }
    }

    private void validateContentMedia(MultipartFile file) {
        String ct = file.getContentType() != null ? file.getContentType() : "";
        boolean isImage = IMAGE_TYPES.contains(ct);
        boolean isVideo = VIDEO_TYPES.contains(ct);
        boolean isPdf = PDF_TYPES.contains(ct);
        boolean isAudio = AUDIO_TYPES.contains(ct)
                || (file.getOriginalFilename() != null && file.getOriginalFilename().matches("(?i).+\\.(mp3|wav|aac|m4a|ogg|flac)$"));
        if (!isImage && !isVideo && !isPdf && !isAudio) {
            throw new BusinessException("INVALID_FILE_TYPE",
                    "Média : image (jpeg, png, webp), vidéo (mp4, webm, mov), audio (mp3, wav, aac, ogg) ou PDF");
        }
        if (isImage && file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException("FILE_TOO_LARGE", "Taille maximale image : 30 Mo");
        }
        if (isVideo && file.getSize() > MAX_VIDEO_BYTES) {
            throw new BusinessException("FILE_TOO_LARGE", "Taille maximale vidéo : 500 Mo");
        }
        if (isPdf && file.getSize() > MAX_CONTENT_PDF_BYTES) {
            throw new BusinessException("FILE_TOO_LARGE", "Taille maximale PDF : 50 Mo");
        }
        if (isAudio && file.getSize() > MAX_CONTENT_PDF_BYTES) {
            throw new BusinessException("FILE_TOO_LARGE", "Taille maximale audio : 50 Mo");
        }
    }
}
