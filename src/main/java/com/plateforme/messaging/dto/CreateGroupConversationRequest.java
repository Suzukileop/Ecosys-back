package com.plateforme.messaging.dto;

import java.util.List;
import java.util.UUID;

public record CreateGroupConversationRequest(
        String title,
        List<UUID> memberIds
) {
}
