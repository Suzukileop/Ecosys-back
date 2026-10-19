package com.plateforme.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Changing a password needs {@code currentPassword}. An account without one (created with Google)
 * leaves it empty and sends {@code verificationCode} instead — the code e-mailed by
 * POST /api/user/account/password/code.
 */
public record ChangePasswordRequest(
        String currentPassword,
        @Size(max = 16)
        String verificationCode,
        @NotBlank
        @Size(min = 8, max = 128, message = "Password must be 8–128 characters")
        String newPassword
) {}
