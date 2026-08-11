package com.plateforme.ecosystem.dto;

import jakarta.validation.constraints.*;

import java.util.List;

public record NicheRequestFormDto(
        @NotBlank @Size(max = 200) String nicheTheme,
        @NotBlank @Size(max = 3000) String description,
        @NotBlank @Size(max = 10) String language,
        @Min(1) @Max(14) int nbPostsPerWeek,
        @NotEmpty @Size(min = 1, max = 5) List<@NotBlank String> platforms,
        String refType,
        String refMctCode,
        String refExternalUrl
) {
}
