package com.plateforme.marketplace.dto;

import com.plateforme.marketplace.entity.ContentTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ShareRequest(
        @NotNull ContentTargetType targetType,
        @NotNull UUID targetId,
        @NotBlank @Size(max = 50) String platform
) {
}
