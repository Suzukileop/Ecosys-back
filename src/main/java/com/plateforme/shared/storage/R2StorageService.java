package com.plateforme.shared.storage;

import com.plateforme.shared.config.R2StorageProperties;
import com.plateforme.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
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
        return uploadPublicImage(objectKey, file.getBytes(), contentType);
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
        if (ImageRenditions.isSupported(contentType)) {
            return uploadPrivateImage(objectKey, file.getBytes(), contentType);
        }
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
        /* Derivatives live beside the canonical object under a "__w<width>" suffix. */
        for (int width : ImageRenditions.DERIVATIVE_WIDTHS) {
            for (String type : new String[] {"image/jpeg", "image/png"}) {
                try {
                    s3Client.deleteObject(DeleteObjectRequest.builder()
                            .bucket(r2.bucket())
                            .key(ImageRenditions.derivativeKey(objectKey, width, type))
                            .build());
                } catch (RuntimeException ex) {
                    log.debug("R2 derivative delete skipped key={} w={}: {}", objectKey, width, ex.toString());
                }
            }
        }
        log.info("R2 DeleteObject bucket={} key={}", r2.bucket(), objectKey);
    }

    @Override
    public int deleteByPrefix(String prefix) throws IOException {
        LocalDevStorageService.requireFolderPrefix(prefix);
        List<ObjectIdentifier> keys = s3Client.listObjectsV2Paginator(ListObjectsV2Request.builder()
                        .bucket(r2.bucket())
                        .prefix(prefix)
                        .build())
                .contents()
                .stream()
                .map(object -> ObjectIdentifier.builder().key(object.key()).build())
                .toList();
        /* DeleteObjects accepts at most 1000 keys per call. */
        for (int from = 0; from < keys.size(); from += 1000) {
            List<ObjectIdentifier> batch = keys.subList(from, Math.min(from + 1000, keys.size()));
            s3Client.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(r2.bucket())
                    .delete(Delete.builder().objects(batch).quiet(true).build())
                    .build());
        }
        log.info("R2 DeleteByPrefix bucket={} prefix={} objects={}", r2.bucket(), prefix, keys.size());
        return keys.size();
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
            throw new BusinessException("FILE_REQUIRED", "Please choose a file.");
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
                        "Supported video formats: MP4, MOV and WebM.");
            }
            if (file.getSize() > MAX_VIDEO_BYTES) {
                throw new BusinessException("FILE_TOO_LARGE", "Videos must be 500 MB or less.");
            }
        } else if (audioPath) {
            if (file.getSize() > 50L * 1024 * 1024) {
                throw new BusinessException("FILE_TOO_LARGE", "Taille maximale audio : 50 Mo");
            }
        } else {
            if (!IMAGE_TYPES.contains(ct)) {
                throw new BusinessException("INVALID_FILE_TYPE",
                        "Supported image formats: JPEG, PNG and WebP.");
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
                    "Thumbnails must be an image (JPEG, PNG, WebP) or a video (MP4, WebM, MOV).");
        }
        if (isImage && file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException("FILE_TOO_LARGE", "Taille maximale image : 30 Mo");
        }
        if (isVideo && file.getSize() > MAX_THUMBNAIL_VIDEO_BYTES) {
            throw new BusinessException("FILE_TOO_LARGE", "Thumbnail videos must be 25 MB or less.");
        }
    }

    private void validateProfileImage(MultipartFile file) {
        String ct = file.getContentType() != null ? file.getContentType() : "";
        if (!IMAGE_TYPES.contains(ct)) {
            throw new BusinessException("INVALID_FILE_TYPE",
                    "Profile photos must be JPEG, PNG or WebP.");
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
                    "Supported media: images (JPEG, PNG, WebP), videos (MP4, WebM, MOV), audio (MP3, WAV, AAC, OGG) or PDF.");
        }
        if (isImage && file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException("FILE_TOO_LARGE", "Taille maximale image : 30 Mo");
        }
        if (isVideo && file.getSize() > MAX_VIDEO_BYTES) {
            throw new BusinessException("FILE_TOO_LARGE", "Videos must be 500 MB or less.");
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
