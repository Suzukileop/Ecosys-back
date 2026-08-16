package com.plateforme.shared.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationDto(
        UUID id,
        String type,
        String title,
        String message,
        Boolean isRead,
        String channel,
        UUID refId,
        UUID refSecondaryId,
        LocalDateTime createdAt,
        String actorFullName,
        String actorAvatarUrl,
        Boolean actorProfileAvailable
) {
    public NotificationDto(
            UUID id,
            String type,
            String title,
            String message,
            Boolean isRead,
            String channel,
            UUID refId,
            UUID refSecondaryId,
            LocalDateTime createdAt
    ) {
        this(id, type, title, message, isRead, channel, refId, refSecondaryId, createdAt, null, null, null);
    }
}
