package com.plateforme.user.dto;

import java.util.UUID;

public record ProfileLinkDto(
        UUID id,
        String type,
        String label,
        String url,
        int sortOrder,
        String platform,
        String iconUrl
) {
    public ProfileLinkDto {
        type = type != null ? type.trim().toUpperCase() : "CUSTOM";
    }
}
