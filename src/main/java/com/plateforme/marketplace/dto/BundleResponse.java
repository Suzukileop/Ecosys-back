package com.plateforme.marketplace.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record BundleResponse(
        UUID id,
        UUID creatorId,
        String title,
        String description,
        int priceCents,
        String currency,
        String thumbnailUrl,
        Integer discountPercent,
        boolean isPublished,
        List<UUID> productIds,
        LocalDateTime createdAt
) {}
