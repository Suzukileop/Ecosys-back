package com.plateforme.messaging.dto;

import com.plateforme.messaging.entity.ConversationType;

import java.time.LocalDateTime;
import java.util.UUID;

public record ConversationSummaryDto(
        UUID id,
        ConversationType type,
        String title,
        String coverUrl,
        UUID otherUserId,
        String otherUserFullName,
        String otherUserAvatarUrl,
        int participantCount,
        String lastMessageContent,
        UUID lastMessageId,
        UUID lastMessageSenderId,
        LocalDateTime lastMessageAt,
        LocalDateTime otherUserLastReadAt,
        LocalDateTime updatedAt,
        long unreadCount,
        boolean guestSession,
        LocalDateTime guestExpiresAt
) {
}
