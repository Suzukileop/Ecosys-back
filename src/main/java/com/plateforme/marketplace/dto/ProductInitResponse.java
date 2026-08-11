package com.plateforme.marketplace.dto;

/**
 * Aggregated response for the product detail page bootstrap.
 * Replaces 4–5 parallel client-side fetches with a single request.
 */
public record ProductInitResponse(
        MarketplaceProductResponse product,
        ProductReviewSummaryResponse reviewSummary,
        ReactionCountsResponse reactionCounts,
        /** null when the user is not authenticated or does not own the product */
        ProductOwnershipResponse ownership
) {}
