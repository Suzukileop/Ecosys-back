package com.plateforme.messaging.dto;

import com.plateforme.messaging.entity.ConversationType;

import java.time.LocalDateTime;
import java.util.UUID;

public record PendingConversationInviteDto(
        UUID id,
        UUID conversationId,
        ConversationType conversationType,
        String conversationTitle,
        String inviterName,
        String inviterAvatarUrl,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {
}
