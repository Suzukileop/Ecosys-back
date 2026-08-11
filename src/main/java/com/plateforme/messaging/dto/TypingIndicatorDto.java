package com.plateforme.messaging.dto;

public record TypingIndicatorDto(
        String userId,
        String userName,
        boolean typing
) {}
