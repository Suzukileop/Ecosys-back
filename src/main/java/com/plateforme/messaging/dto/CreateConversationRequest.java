package com.plateforme.messaging.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateConversationRequest(
        @NotNull
        UUID otherUserId
) {
}
