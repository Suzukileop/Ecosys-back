package com.plateforme.user.dto;

import java.time.LocalDateTime;

public record AccountSecurityDto(
        String email,
        boolean emailVerified,
        String authProvider,
        boolean hasPassword,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        long activeSessions
) {}
