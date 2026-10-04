package com.plateforme.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code currentPassword} may be empty for accounts created with Google that never set a password. */
public record ChangePasswordRequest(
        String currentPassword,
        @NotBlank
        @Size(min = 8, max = 128, message = "Password must be 8–128 characters")
        String newPassword
) {}
