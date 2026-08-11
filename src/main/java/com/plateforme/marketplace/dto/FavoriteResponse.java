package com.plateforme.marketplace.dto;

import com.plateforme.marketplace.entity.ContentTargetType;

import java.time.LocalDateTime;
import java.util.UUID;

public record FavoriteResponse(
        UUID id,
        ContentTargetType targetType,
        UUID targetId,
        LocalDateTime createdAt
) {
}
