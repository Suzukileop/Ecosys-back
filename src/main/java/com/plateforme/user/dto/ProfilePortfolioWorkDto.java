package com.plateforme.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ProfilePortfolioWorkDto(
        UUID id,
        int sortOrder,
        @Size(max = 80)
        String role,
        @Size(max = 80)
        String category,
        @Size(max = 120)
        String title,
        @Size(max = 2000)
        String description,
        @Size(max = 12)
        List<@Size(max = 40) String> stack,
        @NotBlank
        String imageUrl,
        @Size(max = 500)
        String link
) {}
