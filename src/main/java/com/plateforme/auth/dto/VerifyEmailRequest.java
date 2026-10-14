package com.plateforme.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyEmailRequest(
        @Email(message = "Enter a valid email address.")
        @NotBlank(message = "Email is required.")
        String email,

        @NotBlank(message = "Enter the 6-digit code.")
        @Pattern(regexp = "^\\s*\\d{6}\\s*$", message = "Enter the 6-digit code.")
        String code
) {}
