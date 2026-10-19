package com.plateforme.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.plateforme.user.dto.UserDto;

/**
 * {@code refreshToken} is filled by the services but never reaches the browser: the controllers
 * move it into an HttpOnly cookie (RefreshTokenCookies) and send {@link #withoutRefreshToken()}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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

    public AuthResponse withoutRefreshToken() {
        return new AuthResponse(accessToken, null, tokenType, expiresIn, user);
    }
}
