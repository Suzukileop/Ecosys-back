package com.plateforme.marketplace.dto;

import java.util.List;
import java.util.UUID;

public record MinimalUserDto(
        UUID id,
        String fullName,
        String avatarUrl,
        String appRole,
        String specialite,
        List<String> specialties
) {
    public MinimalUserDto(UUID id, String fullName, String avatarUrl) {
        this(id, fullName, avatarUrl, null, null, List.of());
    }

    public MinimalUserDto(UUID id, String fullName, String avatarUrl, String appRole) {
        this(id, fullName, avatarUrl, appRole, null, List.of());
    }
}
