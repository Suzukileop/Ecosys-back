package com.plateforme.marketplace.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProductReviewResponse(
        UUID id,
        UUID userId,
        String userName,
        String userAvatarUrl,
        int rating,
        String comment,
        LocalDateTime createdAt,
        int helpfulYesCount,
        int helpfulNoCount,
        Boolean userHelpfulVote
) {}
