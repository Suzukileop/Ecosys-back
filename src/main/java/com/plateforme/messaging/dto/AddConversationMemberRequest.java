package com.plateforme.messaging.dto;

import java.util.UUID;

public record AddConversationMemberRequest(
        UUID userId
) {
}
