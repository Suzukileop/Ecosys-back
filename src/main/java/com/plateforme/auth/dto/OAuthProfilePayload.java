package com.plateforme.auth.dto;

public record OAuthProfilePayload(
        String provider,
        String providerUserId,
        String email,
        String fullName,
        String avatarUrl
) {
}
