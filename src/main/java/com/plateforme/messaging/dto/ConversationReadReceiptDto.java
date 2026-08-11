package com.plateforme.messaging.dto;

public record ConversationReadReceiptDto(
        String userId,
        String readAt
) {}
