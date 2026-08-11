package com.plateforme.messaging.dto;

public record CreateConversationInviteRequest(
        Integer expiresInHours,
        Integer maxUses
) {
}
