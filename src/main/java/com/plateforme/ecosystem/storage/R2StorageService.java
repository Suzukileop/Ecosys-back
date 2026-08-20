package com.plateforme.ecosystem.storage;

import com.plateforme.ecosystem.config.R2StorageProperties;
import com.plateforme.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.r2", name = "enabled", havingValue = "true")
public class R2StorageService implements StorageService {

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

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final R2StorageProperties r2;

    @Override
    public String uploadPublicFile(String objectKey, InputStream inputStream, long contentLength, String contentType)
            throws IOException {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IOException("Clé objet vide");
        }
        if (r2.publicBaseUrl().isEmpty()) {
            throw new IOException(
                    "app.r2.public-base-url est requis (ex. https://pub-….r2.dev depuis Cloudflare R2 → Public Development URL)");
        }

        String ct = contentType != null && !contentType.isBlank() ? contentType : "application/octet-stream";

        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(r2.bucket())
                .key(objectKey)
                .contentType(ct)
                .contentLength(contentLength)
                .cacheControl("public, max-age=31536000, immutable")
                .build();

        s3Client.putObject(req, RequestBody.fromInputStream(inputStream, contentLength));

        String url = publicUrl(objectKey);
        log.info("R2 PutObject bucket={} key={} publicUrl={}", r2.bucket(), objectKey, url);
        return url;
    }

    @Override
    public String uploadFile(MultipartFile file, String objectKey) throws IOException {
        validateMultipart(file, objectKey);
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        byte[] payload = file.getBytes();
        var normalized = DisplayImageNormalizer.maybeNormalize(payload, contentType);
        if (normalized.isPresent()) {
            payload = normalized.get().bytes();
            contentType = normalized.get().contentType();
        }
        return uploadPublicFile(objectKey, new java.io.ByteArrayInputStream(payload), payload.length, contentType);
    }

    @Override
    public String uploadPrivateFile(String objectKey, InputStream inputStream, long contentLength, String contentType)
            throws IOException {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IOException("Clé objet vide");
        }
        String ct = contentType != null && !contentType.isBlank() ? contentType : "application/octet-stream";

        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(r2.bucket())
                .key(objectKey)
                .contentType(ct)
                .contentLength(contentLength)
                .build();

        s3Client.putObject(req, RequestBody.fromInputStream(inputStream, contentLength));
        log.info("R2 private PutObject bucket={} key={}", r2.bucket(), objectKey);
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
        if (objectKey == null || objectKey.isBlank()) {
            throw new IOException("Clé objet vide");
        }
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(r2.bucket())
                .key(objectKey)
                .build());
        log.info("R2 DeleteObject bucket={} key={}", r2.bucket(), objectKey);
    }

    @Override
    public String generateSignedUrl(String objectKey, int expiryMinutes) throws IOException {
        return presignGetObject(objectKey, expiryMinutes, null);
    }

    @Override
    public String generateSignedDownloadUrl(String objectKey, String downloadFilename, int expiryMinutes)
            throws IOException {
        String disposition = "attachment; filename=\"" + sanitizeDownloadFilename(downloadFilename) + "\"";
        return presignGetObject(objectKey, expiryMinutes, disposition);
    }

    private String presignGetObject(String objectKey, int expiryMinutes, String contentDisposition)
            throws IOException {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IOException("Clé objet vide");
        }
        int minutes = Math.max(1, expiryMinutes);
        GetObjectRequest.Builder getReqBuilder = GetObjectRequest.builder()
                .bucket(r2.bucket())
                .key(objectKey);
        if (contentDisposition != null && !contentDisposition.isBlank()) {
            getReqBuilder.responseContentDisposition(contentDisposition);
        }
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(minutes))
                .getObjectRequest(getReqBuilder.build())
                .build());
        return presigned.url().toString();
    }

    private static String sanitizeDownloadFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "download";
        }
        return filename.replace("\"", "").replace("\r", "").replace("\n", "").trim();
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

    private String publicUrl(String objectKey) {
        return trimTrailingSlash(r2.publicBaseUrl()) + "/" + objectKey.replace("\\", "/");
    }

    private static String trimTrailingSlash(String s) {
        String out = s.trim();
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }
}
