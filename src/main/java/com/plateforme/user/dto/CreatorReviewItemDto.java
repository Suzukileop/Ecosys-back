package com.plateforme.user.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreatorReviewItemDto(
        UUID id,
        String reviewerName,
        int rating,
        String comment,
        boolean wouldRecommend,
        LocalDateTime createdAt
) {}
