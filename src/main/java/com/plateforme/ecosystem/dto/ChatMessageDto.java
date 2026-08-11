package com.plateforme.ecosystem.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChatMessageDto(
        UUID id,
        String roomId,
        UUID senderId,
        String senderName,
        String content,
        LocalDateTime sentAt,
        Boolean isRead,
        String senderType
) {
}
