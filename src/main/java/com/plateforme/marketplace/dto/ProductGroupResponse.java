package com.plateforme.marketplace.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ProductGroupResponse(
        UUID id,
        UUID creatorId,
        String name,
        int sortOrder,
        int productCount,
        List<UUID> productIds,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
