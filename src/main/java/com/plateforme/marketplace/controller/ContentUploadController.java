package com.plateforme.marketplace.controller;

import com.plateforme.shared.storage.StorageObjectKeys;
import com.plateforme.shared.storage.StorageService;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/creator/content/uploads")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CREATOR')")
@Tag(name = "Content Uploads", description = "Portfolio content media uploads")
@SecurityRequirement(name = "bearerAuth")
public class ContentUploadController {

    private static final long MAX_EXPERIENCE_MEDIA_BYTES = 100L * 1024 * 1024;
    private static final Set<String> EXPERIENCE_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> EXPERIENCE_VIDEO_TYPES = Set.of("video/mp4", "video/quicktime", "video/webm");
    private static final Set<String> EXPERIENCE_EXTENSIONS =
            Set.of(".jpg", ".jpeg", ".png", ".webp", ".mp4", ".mov", ".webm");

    private final StorageService storageService;

    @Operation(summary = "Upload main content media (image, video, audio, or PDF)")
    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadMedia(@RequestParam("file") MultipartFile file)
            throws IOException {
        UUID creatorId = getCurrentUserId();
        String objectKey = StorageObjectKeys.uniqueObjectKey(
                "content/public", creatorId, file.getOriginalFilename());
        String url = storageService.uploadFile(file, objectKey);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @Operation(summary = "Upload experience media (image or video, 100 MB max)")
    @PostMapping(value = "/experience-media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadExperienceMedia(@RequestParam("file") MultipartFile file)
            throws IOException {
        validateExperienceMedia(file);
        UUID creatorId = getCurrentUserId();
        String objectKey = StorageObjectKeys.uniqueObjectKey(
                "content/public/experience", creatorId, file.getOriginalFilename());
        String url = storageService.uploadFile(file, objectKey);
        return ResponseEntity.ok(Map.of("url", url));
    }

    private static void validateExperienceMedia(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("FILE_REQUIRED", "File is required");
        }
        if (file.getSize() > MAX_EXPERIENCE_MEDIA_BYTES) {
            throw new BusinessException("FILE_TOO_LARGE", "Experience media must be 100 MB or less");
        }
        String contentType = file.getContentType() != null
                ? file.getContentType().toLowerCase(Locale.ROOT)
                : "";
        boolean image = EXPERIENCE_IMAGE_TYPES.contains(contentType);
        boolean video = EXPERIENCE_VIDEO_TYPES.contains(contentType);
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        int dot = name.lastIndexOf('.');
        String extension = dot >= 0 ? name.substring(dot) : "";
        if ((!image && !video) || !EXPERIENCE_EXTENSIONS.contains(extension)) {
            throw new BusinessException("INVALID_FILE_TYPE",
                    "Experience media: image (jpeg, png, webp) or video (mp4, mov, webm)");
        }
        byte[] header = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(header, 0, header.length);
        }
        if (!matchesSignature(header, read, image)) {
            throw new BusinessException("INVALID_FILE_TYPE", "File content does not match its declared type");
        }
    }

    /** Magic-byte check so a renamed executable or script cannot pass as an image or video. */
    private static boolean matchesSignature(byte[] h, int read, boolean image) {
        if (read < 12) return false;
        if (image) {
            boolean jpeg = (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
            boolean png = (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
            boolean webp = h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                    && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
            return jpeg || png || webp;
        }
        boolean isoMedia = h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p';
        boolean webm = (h[0] & 0xFF) == 0x1A && (h[1] & 0xFF) == 0x45
                && (h[2] & 0xFF) == 0xDF && (h[3] & 0xFF) == 0xA3;
        return isoMedia || webm;
    }

    private UUID getCurrentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
