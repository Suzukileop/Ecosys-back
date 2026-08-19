package com.plateforme.user.dto;

import jakarta.validation.constraints.Size;

public record ProfileAboutUsFounderDto(
        String logoUrl,
        @Size(max = 100) String name,
        @Size(max = 120) String function
) {}
