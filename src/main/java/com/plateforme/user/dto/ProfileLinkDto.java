package com.plateforme.user.dto;

import java.util.UUID;

public record ProfileLinkDto(
        UUID id,
        String type,
        String label,
        String url,
        int sortOrder,
        String platform,
        String iconUrl,
        /** When true the link is left out of the generated CV. */
        Boolean hideFromCv
) {
    public ProfileLinkDto {
        type = type != null ? type.trim().toUpperCase() : "CUSTOM";
    }
}
