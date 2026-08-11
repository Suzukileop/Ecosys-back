package com.plateforme.marketplace.controller;

import com.plateforme.ecosystem.storage.StorageObjectKeys;
import com.plateforme.ecosystem.storage.StorageService;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/creator/content/uploads")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CREATOR')")
@Tag(name = "Content Uploads", description = "Portfolio content media uploads")
@SecurityRequirement(name = "bearerAuth")
public class ContentUploadController {

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

    private UUID getCurrentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
