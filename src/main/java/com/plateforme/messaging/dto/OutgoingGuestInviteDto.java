package com.plateforme.messaging.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record OutgoingGuestInviteDto(
        UUID id,
        UUID inviteeUserId,
        String inviteeName,
        String inviteeAvatarUrl,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {
}
