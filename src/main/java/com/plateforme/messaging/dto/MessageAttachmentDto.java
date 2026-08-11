package com.plateforme.messaging.dto;

import java.util.UUID;

public record MessageAttachmentDto(
        UUID id,
        String fileName,
        String contentType,
        long sizeBytes
) {
}
