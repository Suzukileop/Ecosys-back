package com.plateforme.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ProfileTeamMemberDto(
        UUID id,
        int sortOrder,
        @NotBlank
        @Size(max = 100)
        String name,
        @NotBlank
        @Size(max = 120)
        String responsibility,
        String imageUrl,
        @Valid
        @Size(max = 6)
        List<ProfileTeamSocialLinkDto> socialLinks
) {}
