package com.plateforme.user.dto;

import java.util.List;
import java.util.Map;

public record CreatorReputationDto(
        Double averageRating,
        long reviewCount,
        int recommendPercent,
        List<String> trustBadges,
        List<CreatorReviewItemDto> recentReviews,
        Map<Integer, Integer> ratingDistribution
) {}
