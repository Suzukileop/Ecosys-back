package com.plateforme.marketplace.dto;

public record ProductReviewSummaryResponse(
        Double averageRating,
        int reviewCount,
        int rating5Count,
        int rating4Count,
        int rating3Count,
        int rating2Count,
        int rating1Count
) {}
