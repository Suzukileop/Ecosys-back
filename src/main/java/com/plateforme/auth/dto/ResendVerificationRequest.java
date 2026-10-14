package com.plateforme.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendVerificationRequest(
        @Email(message = "Enter a valid email address.")
        @NotBlank(message = "Email is required.")
        String email,

        @Size(max = 16)
        String locale
) {}
