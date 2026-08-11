package com.plateforme.user.dto;

import java.util.UUID;

public record FaqItemDto(
        UUID id,
        int sortOrder,
        String question,
        String answer
) {}
