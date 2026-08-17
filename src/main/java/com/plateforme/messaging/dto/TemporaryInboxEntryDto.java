package com.plateforme.messaging.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TemporaryInboxEntryDto(
        String entryType,
        UUID id,
        UUID conversationId,
        String conversationTitle,
        String headline,
        String subtitle,
        String avatarUrl,
        LocalDateTime occurredAt,
        UUID inviteId,
        boolean canOpen,
        List<TemporaryInboxMemberDto> members
) {
}
