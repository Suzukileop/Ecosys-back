package com.plateforme.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuthCompleteRegistrationRequest(
        @NotBlank String code,
        /** Ignored — all accounts are created as CREATOR. Kept for API compatibility. */
        String role
) {
}
