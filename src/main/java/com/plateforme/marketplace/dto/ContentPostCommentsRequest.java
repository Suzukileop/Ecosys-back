package com.plateforme.marketplace.dto;

import jakarta.validation.constraints.NotNull;

public record ContentPostCommentsRequest(
        @NotNull Boolean commentsEnabled
) {}
