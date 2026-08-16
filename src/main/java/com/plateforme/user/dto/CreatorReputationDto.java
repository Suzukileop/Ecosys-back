package com.plateforme.user.dto;

import java.util.List;
import java.util.Map;

public record CreatorReputationDto(
        Double averageRating,
        long reviewCount,
        /** Kept for studio / portfolio recommendation panels — not used by public Response rate. */
        int recommendPercent,
        /** Legacy sample size for recommend %; not used by public trust strip Response rate. */
        long completedMissionsCount,
        /** Share of Discuss-inbound DMs the provider eventually answered (0–100), or null if none. */
        Integer responseRatePercent,
        /** Discuss conversations initiated by a client that include at least one client message. */
        long inboundConversationCount,
        /** Average first-reply latency hint, e.g. "Typically replies within 3 h". */
        String typicallyRepliesWithinLabel,
        List<String> trustBadges,
        List<CreatorReviewItemDto> recentReviews,
        Map<Integer, Integer> ratingDistribution
) {}
