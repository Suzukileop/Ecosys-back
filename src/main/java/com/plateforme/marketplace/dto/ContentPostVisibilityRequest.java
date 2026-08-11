package com.plateforme.marketplace.dto;

import jakarta.validation.constraints.NotNull;

public record ContentPostVisibilityRequest(
        @NotNull Boolean isPublic
) {}
