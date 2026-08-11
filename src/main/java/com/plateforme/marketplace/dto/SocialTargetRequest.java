package com.plateforme.marketplace.dto;

import com.plateforme.marketplace.entity.ContentTargetType;
import com.plateforme.marketplace.entity.ReactionType;
import jakarta.validation.constraints.NotNull;

public record SocialTargetRequest(
        @NotNull ContentTargetType targetType,
        @NotNull java.util.UUID targetId,
        ReactionType type
) {
}
