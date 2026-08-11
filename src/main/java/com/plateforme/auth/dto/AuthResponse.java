package com.plateforme.auth.dto;

import com.plateforme.user.dto.UserDto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserDto user
) {
    public AuthResponse(String accessToken, String refreshToken, long expiresIn, UserDto user) {
        this(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
