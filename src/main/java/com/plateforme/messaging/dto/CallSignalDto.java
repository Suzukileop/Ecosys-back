package com.plateforme.messaging.dto;

public record CallSignalDto(
        String type,
        String payload,
        String fromUserId
) {
}
