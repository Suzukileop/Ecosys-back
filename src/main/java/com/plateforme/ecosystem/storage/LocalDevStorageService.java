package com.plateforme.ecosystem.storage;

import com.plateforme.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
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
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.r2", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LocalDevStorageService implements StorageService {

    private final StorageSignature storageSignature;

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
        return uploadPublicImage(objectKey, file.getBytes(), contentType);
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
        if (ImageRenditions.isSupported(contentType)) {
            return uploadPrivateImage(objectKey, file.getBytes(), contentType);
        }
        return uploadPrivateFile(objectKey, file.getInputStream(), file.getSize(), contentType);
    }

    @Override
    public void deleteFile(String objectKey) throws IOException {
        Path target = resolveTarget(objectKey);
        Files.deleteIfExists(target);
        /* Derivatives live beside the canonical file under a "__w<width>" suffix. */
        for (int width : ImageRenditions.DERIVATIVE_WIDTHS) {
            for (String type : new String[] {"image/jpeg", "image/png"}) {
                Files.deleteIfExists(resolveTarget(ImageRenditions.derivativeKey(objectKey, width, type)));
            }
        }
        log.info("Fichier local supprimé clé={}", objectKey);
    }

    @Override
    public int deleteByPrefix(String prefix) throws IOException {
        requireFolderPrefix(prefix);
        Path folder = resolveTarget(prefix);
        if (!Files.isDirectory(folder)) {
            return 0;
        }
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(folder)) {
            paths = walk.sorted(Comparator.reverseOrder()).toList();
        }
        int deleted = 0;
        for (Path path : paths) {
            boolean isFile = Files.isRegularFile(path);
            Files.deleteIfExists(path);
            if (isFile) {
                deleted++;
            }
        }
        log.info("Dossier local supprimé préfixe={} fichiers={}", prefix, deleted);
        return deleted;
    }

    static void requireFolderPrefix(String prefix) throws IOException {
        if (prefix == null || prefix.isBlank() || !prefix.endsWith("/") || prefix.split("/").length < 2) {
            throw new IOException("Préfixe invalide : " + prefix);
        }
    }

    @Override
    public String generateSignedUrl(String objectKey, int expiryMinutes) throws IOException {
        return signIfPrivate(UriComponentsBuilder.fromUriString(buildPublicUrl(objectKey)), objectKey, expiryMinutes)
                .build()
                .toUriString();
    }

    @Override
    public String generateSignedDownloadUrl(String objectKey, String downloadFilename, int expiryMinutes)
            throws IOException {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(buildPublicUrl(objectKey))
                .queryParam("disposition", "attachment")
                .queryParam("filename", downloadFilename != null && !downloadFilename.isBlank()
                        ? downloadFilename
                        : "download");
        return signIfPrivate(builder, objectKey, expiryMinutes).build().toUriString();
    }

    /**
     * Private keys leave with a time-limited signature; public ones stay bare so their URLs remain
     * stable and cacheable. See {@link StorageSignature}.
     */
    private UriComponentsBuilder signIfPrivate(UriComponentsBuilder builder, String objectKey, int expiryMinutes) {
        if (!StorageSignature.isPrivateKey(objectKey)) {
            return builder;
        }
        long expiresAt = storageSignature.expiryFromNow(expiryMinutes);
        return builder
                .queryParam(StorageSignature.EXPIRY_PARAM, expiresAt)
                .queryParam(StorageSignature.SIGNATURE_PARAM, storageSignature.sign(objectKey, expiresAt));
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
}
