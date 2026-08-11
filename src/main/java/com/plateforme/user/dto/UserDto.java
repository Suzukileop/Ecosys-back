package com.plateforme.user.dto;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record UserDto(
        UUID id,
        String email,
        String fullName,
        String avatarUrl,
        Set<String> roles,
        LocalDateTime createdAt,
        Boolean emailVerified
) {}
