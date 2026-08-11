package com.plateforme.messaging.dto;

public record MessageDeliveryReceiptDto(
        String messageId,
        String userId,
        String deliveredAt
) {}
