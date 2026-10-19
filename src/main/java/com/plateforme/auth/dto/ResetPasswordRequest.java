package com.plateforme.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @Email(message = "Enter a valid email address.")
        @NotBlank(message = "Email is required.")
        String email,

        @NotBlank(message = "Enter the 6-digit code.")
        @Pattern(regexp = "^\\s*\\d{6}\\s*$", message = "Enter the 6-digit code.")
        String code,

        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 128, message = "Password must be 8–128 characters")
        String newPassword
) {}
