package com.plateforme.user.dto;

import java.util.List;
import java.util.UUID;

public record ProfileServiceDto(
        UUID id,
        int sortOrder,
        String title,
        String description,
        Integer basePriceCents,
        String deadline,
        List<String> tasks
) {}
