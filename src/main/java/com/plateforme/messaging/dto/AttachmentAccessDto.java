package com.plateforme.messaging.dto;

public record AttachmentAccessDto(
        String url,
        String fileName,
        String contentType
) {
}
