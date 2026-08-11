package com.plateforme.messaging.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ConversationGuestDto(
        UUID inviteId,
        UUID guestUserId,
        String guestName,
        String guestAvatarUrl,
        UUID inviterUserId,
        String inviterName,
        LocalDateTime expiresAt,
        LocalDateTime invitedAt
) {
}
