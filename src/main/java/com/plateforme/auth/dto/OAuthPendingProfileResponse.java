package com.plateforme.auth.dto;

public record OAuthPendingProfileResponse(
        String email,
        String fullName,
        String avatarUrl,
        String provider
) {
}
