package com.plateforme.user.dto;

import java.util.UUID;

public record ProfileEducationEntryDto(
        UUID id,
        int sortOrder,
        String schoolYear,
        String title,
        String institution
) {}
