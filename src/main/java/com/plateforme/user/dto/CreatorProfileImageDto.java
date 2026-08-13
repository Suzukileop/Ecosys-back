package com.plateforme.user.dto;

import com.plateforme.user.entity.CreatorProfileImageKind;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreatorProfileImageDto(
        UUID id,
        CreatorProfileImageKind kind,
        String url,
        LocalDateTime createdAt,
        boolean current
) {
}
