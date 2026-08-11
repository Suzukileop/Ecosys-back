package com.plateforme.marketplace.controller;

import com.plateforme.marketplace.dto.BundleRequest;
import com.plateforme.marketplace.dto.BundleResponse;
import com.plateforme.marketplace.service.MarketplaceBundleService;
import com.plateforme.shared.dto.PagedResponse;
import com.plateforme.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/creator/bundles")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('CREATOR')")
@Tag(name = "Creator Bundles", description = "Marketplace bundle management")
@SecurityRequirement(name = "bearerAuth")
public class MarketplaceBundleController {

    private final MarketplaceBundleService bundleService;

    @Operation(summary = "Create marketplace bundle")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Bundle created"),
            @ApiResponse(responseCode = "400", description = "Invalid data")
    })
    @PostMapping
    public ResponseEntity<BundleResponse> createBundle(@Valid @RequestBody BundleRequest request) {
        BundleResponse body = bundleService.createBundle(getCurrentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "List my marketplace bundles")
    @GetMapping
    public ResponseEntity<PagedResponse<BundleResponse>> getMyBundles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(PagedResponse.fromPage(
                bundleService.getMyBundles(getCurrentUserId(), pageable)));
    }

    @Operation(summary = "Get my marketplace bundle")
    @GetMapping("/{id}")
    public ResponseEntity<BundleResponse> getMyBundle(@PathVariable UUID id) {
        return ResponseEntity.ok(bundleService.getMyBundle(getCurrentUserId(), id));
    }

    @Operation(summary = "Update marketplace bundle")
    @PutMapping("/{id}")
    public ResponseEntity<BundleResponse> updateBundle(
            @PathVariable UUID id,
            @Valid @RequestBody BundleRequest request) {
        return ResponseEntity.ok(bundleService.updateBundle(getCurrentUserId(), id, request));
    }

    @Operation(summary = "Delete marketplace bundle (soft delete)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBundle(@PathVariable UUID id) {
        bundleService.deleteBundle(getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    private UUID getCurrentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
