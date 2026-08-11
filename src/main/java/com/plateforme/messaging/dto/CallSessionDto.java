package com.plateforme.messaging.dto;

import com.plateforme.messaging.entity.CallSessionStatus;
import com.plateforme.messaging.entity.CallType;

import java.time.LocalDateTime;
import java.util.UUID;

public record CallSessionDto(
        UUID id,
        UUID conversationId,
        UUID initiatorId,
        String initiatorName,
        CallType callType,
        CallSessionStatus status,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {
}
