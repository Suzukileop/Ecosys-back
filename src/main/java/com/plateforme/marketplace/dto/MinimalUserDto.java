package com.plateforme.marketplace.dto;

import java.util.List;
import java.util.UUID;

public record MinimalUserDto(
        UUID id,
        String fullName,
        String username,
        String avatarUrl,
        String appRole,
        String specialite,
        List<String> specialties
) {
    public MinimalUserDto(UUID id, String fullName, String username, String avatarUrl) {
        this(id, fullName, username, avatarUrl, null, null, List.of());
    }

    public MinimalUserDto(UUID id, String fullName, String username, String avatarUrl, String appRole) {
        this(id, fullName, username, avatarUrl, appRole, null, List.of());
    }
}
