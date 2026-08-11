package com.plateforme.marketplace.controller;

import com.plateforme.marketplace.dto.AccessUrlResponse;
import com.plateforme.marketplace.dto.OwnedProductResponse;
import com.plateforme.marketplace.dto.ProductOwnershipResponse;
import com.plateforme.marketplace.dto.PurchaseResponse;
import com.plateforme.marketplace.entity.AccessMode;
import com.plateforme.marketplace.service.MarketplaceAccessService;
import com.plateforme.marketplace.service.MarketplacePurchaseService;
import com.plateforme.shared.dto.PagedResponse;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/marketplace")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Marketplace Purchases", description = "Product purchases and access")
@SecurityRequirement(name = "bearerAuth")
public class MarketplacePurchaseController {

    private final MarketplacePurchaseService purchaseService;
    private final MarketplaceAccessService accessService;

    @Operation(summary = "Simulate product purchase")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Purchase completed"),
            @ApiResponse(responseCode = "400", description = "Already purchased or invalid product"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @PostMapping("/products/{id}/purchase")
    public ResponseEntity<PurchaseResponse> purchaseProduct(@PathVariable UUID id) {
        PurchaseResponse body = purchaseService.simulatePurchase(getCurrentUserId(), id);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Simulate bundle purchase")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Bundle purchase completed"),
            @ApiResponse(responseCode = "400", description = "Already purchased or invalid bundle"),
            @ApiResponse(responseCode = "404", description = "Bundle not found")
    })
    @PostMapping("/bundles/{id}/purchase")
    public ResponseEntity<PurchaseResponse> purchaseBundle(@PathVariable UUID id) {
        PurchaseResponse body = purchaseService.simulateBundlePurchase(getCurrentUserId(), id);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "List my purchases")
    @GetMapping("/purchases/me")
    public ResponseEntity<PagedResponse<OwnedProductResponse>> getMyPurchases(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(PagedResponse.fromPage(
                purchaseService.getMyPurchases(getCurrentUserId(), pageable)));
    }

    @Operation(summary = "Check if the current user owns a product")
    @GetMapping("/products/{id}/ownership")
    public ResponseEntity<ProductOwnershipResponse> getProductOwnership(@PathVariable UUID id) {
        return ResponseEntity.ok(purchaseService.getProductOwnership(getCurrentUserId(), id));
    }

    @Operation(summary = "Get signed access URL for a purchase")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Access URL generated"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Purchase not found")
    })
    @GetMapping("/purchases/{purchaseId}/access")
    public ResponseEntity<AccessUrlResponse> getPurchaseAccess(
            @PathVariable UUID purchaseId,
            @RequestParam(defaultValue = "stream") String mode,
            HttpServletRequest request) {
        AccessMode accessMode = parseAccessMode(mode);
        String ip = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        AccessUrlResponse body = accessService.getAccessUrl(
                getCurrentUserId(), purchaseId, accessMode, ip, userAgent);
        return ResponseEntity.ok(body);
    }

    private AccessMode parseAccessMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return AccessMode.STREAM;
        }
        return switch (mode.trim().toLowerCase()) {
            case "download" -> AccessMode.DOWNLOAD;
            case "stream" -> AccessMode.STREAM;
            default -> throw new BusinessException("INVALID_ACCESS_MODE", "mode must be stream or download");
        };
    }

    private UUID getCurrentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
