package com.plateforme.marketplace.dto;

public record ProductReviewComposerStatusResponse(
        ProductReviewResponse latestReview,
        int reviewsPostedToday,
        int dailyReviewLimit,
        boolean canPostReviewToday
) {}
