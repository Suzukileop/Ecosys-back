package com.plateforme.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UpdatePortfolioRequest(
        @NotNull
        @Size(max = 24)
        List<UUID> contentPostIds
) {
    public UpdatePortfolioRequest {
        contentPostIds = contentPostIds != null ? List.copyOf(contentPostIds) : List.of();
    }
}
