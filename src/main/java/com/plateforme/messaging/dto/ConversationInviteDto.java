package com.plateforme.messaging.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ConversationInviteDto(
        UUID id,
        String token,
        String joinPath,
        LocalDateTime expiresAt,
        int maxUses,
        int useCount
) {
}
