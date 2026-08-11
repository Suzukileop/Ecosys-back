package com.plateforme.marketplace.controller;

import com.plateforme.marketplace.dto.MarketplaceProductRequest;
import com.plateforme.marketplace.dto.MarketplaceProductResponse;
import com.plateforme.marketplace.service.MarketplaceProductService;
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
@RequestMapping("/api/creator/products")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('CREATOR')")
@Tag(name = "Creator Products", description = "Marketplace product management")
@SecurityRequirement(name = "bearerAuth")
public class MarketplaceProductController {

    private final MarketplaceProductService productService;

    @Operation(summary = "Create marketplace product")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Product created"),
            @ApiResponse(responseCode = "400", description = "Invalid data"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping
    public ResponseEntity<MarketplaceProductResponse> createProduct(
            @Valid @RequestBody MarketplaceProductRequest request) {
        UUID creatorId = getCurrentUserId();
        MarketplaceProductResponse body = productService.createProduct(creatorId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "List my marketplace products")
    @GetMapping
    public ResponseEntity<PagedResponse<MarketplaceProductResponse>> getMyProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(PagedResponse.fromPage(
                productService.getMyProducts(getCurrentUserId(), pageable)));
    }

    @Operation(summary = "Get my marketplace product")
    @GetMapping("/{id}")
    public ResponseEntity<MarketplaceProductResponse> getMyProduct(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.getMyProduct(getCurrentUserId(), id));
    }

    @Operation(summary = "Update marketplace product")
    @PutMapping("/{id}")
    public ResponseEntity<MarketplaceProductResponse> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody MarketplaceProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(getCurrentUserId(), id, request));
    }

    @Operation(summary = "Delete marketplace product (soft delete)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Publish marketplace product")
    @PatchMapping("/{id}/publish")
    public ResponseEntity<MarketplaceProductResponse> publishProduct(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.setPublished(getCurrentUserId(), id, true));
    }

    @Operation(summary = "Unpublish marketplace product (back to draft)")
    @PatchMapping("/{id}/unpublish")
    public ResponseEntity<MarketplaceProductResponse> unpublishProduct(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.setPublished(getCurrentUserId(), id, false));
    }

    private UUID getCurrentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
