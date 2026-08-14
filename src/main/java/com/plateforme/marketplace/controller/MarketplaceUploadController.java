package com.plateforme.marketplace.controller;

import com.plateforme.ecosystem.storage.StorageObjectKeys;
import com.plateforme.ecosystem.storage.StorageService;
import com.plateforme.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/creator/marketplace/uploads")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('CREATOR')")
@Tag(name = "Marketplace Uploads", description = "Product thumbnail uploads")
@SecurityRequirement(name = "bearerAuth")
public class MarketplaceUploadController {

    private final StorageService storageService;

    @Operation(summary = "Upload product thumbnail (image or short preview video, public URL)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thumbnail uploaded"),
            @ApiResponse(responseCode = "400", description = "Invalid file")
    })
    @PostMapping(value = "/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadThumbnail(@RequestParam("file") MultipartFile file)
            throws IOException {
        UUID creatorId = getCurrentUserId();
        String objectKey = StorageObjectKeys.uniqueObjectKey(
                "marketplace/public", creatorId, file.getOriginalFilename());
        String url = storageService.uploadFile(file, objectKey);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @Operation(summary = "Upload shop cover media (image or short video, public URL)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Shop cover uploaded"),
            @ApiResponse(responseCode = "400", description = "Invalid file")
    })
    @PostMapping(value = "/shop-cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadShopCover(@RequestParam("file") MultipartFile file)
            throws IOException {
        UUID creatorId = getCurrentUserId();
        String objectKey = StorageObjectKeys.uniqueObjectKey(
                "marketplace/public", creatorId, file.getOriginalFilename());
        String url = storageService.uploadFile(file, objectKey);
        return ResponseEntity.ok(Map.of("url", url));
    }

    private UUID getCurrentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
