package com.plateforme.marketplace.dto;

import java.util.UUID;

public record MinimalUserDto(
        UUID id,
        String fullName,
        String avatarUrl
) {}
