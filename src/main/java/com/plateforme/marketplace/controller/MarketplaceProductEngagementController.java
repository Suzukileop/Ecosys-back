package com.plateforme.marketplace.controller;

import com.plateforme.marketplace.dto.ProductReviewComposerStatusResponse;
import com.plateforme.marketplace.dto.ProductReviewHelpfulRequest;
import com.plateforme.marketplace.dto.ProductReviewRequest;
import com.plateforme.marketplace.dto.ProductReviewResponse;
import com.plateforme.marketplace.dto.ProductReviewSummaryResponse;
import com.plateforme.marketplace.dto.ProductViewRecordedResponse;
import com.plateforme.marketplace.service.MarketplaceProductReviewService;
import com.plateforme.marketplace.service.MarketplaceProductViewService;
import com.plateforme.shared.dto.PagedResponse;
import com.plateforme.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/marketplace/products")
@RequiredArgsConstructor
@Tag(name = "Marketplace Product Engagement", description = "Views and client reviews")
public class MarketplaceProductEngagementController {

    private final MarketplaceProductViewService viewService;
    private final MarketplaceProductReviewService reviewService;

    @Operation(summary = "Record a unique product view (one per client account)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "View recorded or already counted"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('CREATOR')")
    @PostMapping("/{id}/view")
    public ResponseEntity<ProductViewRecordedResponse> recordProductView(@PathVariable UUID id) {
        boolean recorded = viewService.recordView(getCurrentUserId(), id);
        return ResponseEntity.ok(new ProductViewRecordedResponse(recorded));
    }

    @Operation(summary = "Product review summary with star distribution")
    @GetMapping("/{id}/reviews/summary")
    public ResponseEntity<ProductReviewSummaryResponse> getReviewSummary(@PathVariable UUID id) {
        return ResponseEntity.ok(reviewService.getReviewSummary(id));
    }

    @Operation(summary = "List product reviews")
    @GetMapping("/{id}/reviews")
    public ResponseEntity<PagedResponse<ProductReviewResponse>> listReviews(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100));
        UUID viewerId = resolveOptionalUserId(authentication);
        return ResponseEntity.ok(reviewService.listReviews(id, viewerId, pageable));
    }

    @Operation(summary = "Get my review composer status for a product")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('CREATOR')")
    @GetMapping("/{id}/reviews/me")
    public ResponseEntity<ProductReviewComposerStatusResponse> getMyReviewStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(reviewService.getMyReviewStatus(getCurrentUserId(), id));
    }

    @Operation(summary = "Post a new product review (clients only, max 3 per day)")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('CREATOR')")
    @PostMapping("/{id}/reviews")
    public ResponseEntity<ProductReviewResponse> submitReview(
            @PathVariable UUID id,
            @Valid @RequestBody ProductReviewRequest request) {
        ProductReviewResponse body = reviewService.submitReview(getCurrentUserId(), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Delete one of my product reviews")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('CREATOR')")
    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<Void> deleteReview(@PathVariable UUID reviewId) {
        reviewService.deleteReview(getCurrentUserId(), reviewId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Vote whether a product review was helpful")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('CREATOR')")
    @PostMapping("/reviews/{reviewId}/helpful")
    public ResponseEntity<ProductReviewResponse> voteReviewHelpful(
            @PathVariable UUID reviewId,
            @Valid @RequestBody ProductReviewHelpfulRequest request) {
        ProductReviewResponse body = reviewService.voteHelpful(
                getCurrentUserId(), reviewId, Boolean.TRUE.equals(request.helpful()));
        return ResponseEntity.ok(body);
    }

    private UUID resolveOptionalUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof User user)) {
            return null;
        }
        return user.getId();
    }

    private UUID getCurrentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
