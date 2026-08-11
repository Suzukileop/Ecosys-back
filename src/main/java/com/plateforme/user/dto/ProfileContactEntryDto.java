package com.plateforme.user.dto;

import java.util.UUID;

public record ProfileContactEntryDto(
        UUID id,
        int sortOrder,
        String value
) {}
