package com.plateforme.marketplace.dto;

import com.plateforme.marketplace.entity.ContentTargetType;
import com.plateforme.marketplace.entity.ReactionType;

public record ReactionCountsResponse(
        ContentTargetType targetType,
        java.util.UUID targetId,
        long likes,
        long dislikes,
        ReactionType userReaction,
        boolean favorited
) {
}
