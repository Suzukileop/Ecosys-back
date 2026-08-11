package com.plateforme.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ProfileTeamSocialLinkDto(
        UUID id,
        @NotBlank
        @Pattern(regexp = "FACEBOOK|X|TWITTER|LINKEDIN|INSTAGRAM|YOUTUBE|GITHUB|WEBSITE|EMAIL|OTHER")
        String platform,
        @Size(max = 80)
        String label,
        @NotBlank
        @Size(max = 500)
        String url,
        int sortOrder
) {}
