package com.plateforme.marketplace.controller;

import com.plateforme.marketplace.dto.ProductInitResponse;
import com.plateforme.marketplace.dto.ProductOwnershipResponse;
import com.plateforme.marketplace.entity.ContentTargetType;
import com.plateforme.marketplace.service.MarketplacePurchaseService;
import com.plateforme.marketplace.service.MarketplaceProductReviewService;
import com.plateforme.marketplace.service.MarketplaceProductService;
import com.plateforme.marketplace.service.MarketplaceSocialService;
import com.plateforme.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Single endpoint that aggregates all data needed to render a product detail page.
 *
 * Replaces these parallel client-side calls:
 *   GET /api/marketplace/products/{id}
 *   GET /api/marketplace/products/{id}/reviews/summary
 *   GET /api/marketplace/social/reactions/counts?targetType=PRODUCT&targetId={id}
 *   GET /api/marketplace/products/{id}/ownership   (auth only)
 */
@RestController
@RequestMapping("/api/marketplace/products")
@RequiredArgsConstructor
@Tag(name = "Marketplace Product Init", description = "Batch bootstrap for product detail page")
public class MarketplaceProductInitController {

    private final MarketplaceProductService productService;
    private final MarketplaceProductReviewService reviewService;
    private final MarketplaceSocialService socialService;
    private final MarketplacePurchaseService purchaseService;

    @Operation(summary = "Bootstrap data for the product detail page (batch endpoint)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aggregated product data"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @GetMapping("/{id}/init")
    public ResponseEntity<ProductInitResponse> getProductInit(
            @PathVariable UUID id,
            Authentication authentication) {

        UUID userId = resolveUserId(authentication);

        var product       = productService.getPublishedProduct(id);
        var reviewSummary = reviewService.getReviewSummary(id);
        var reactions     = socialService.getReactionCounts(ContentTargetType.PRODUCT, id, userId);
        var ownership     = userId != null
                ? purchaseService.getProductOwnership(userId, id)
                : ProductOwnershipResponse.notOwned();

        return ResponseEntity.ok(new ProductInitResponse(product, reviewSummary, reactions, ownership));
    }

    private UUID resolveUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof User user)) {
            return null;
        }
        return user.getId();
    }
}
