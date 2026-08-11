package com.plateforme.messaging.dto;

import com.plateforme.messaging.entity.ParticipantRole;

import java.time.LocalDateTime;
import java.util.UUID;

public record ConversationParticipantDto(
        UUID userId,
        String fullName,
        String avatarUrl,
        ParticipantRole role,
        LocalDateTime joinedAt,
        LocalDateTime lastReadAt
) {}
