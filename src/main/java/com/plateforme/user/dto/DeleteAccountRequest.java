package com.plateforme.user.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code confirmation} must be the literal "DELETE"; {@code password} is required when the account has one. */
public record DeleteAccountRequest(
        String password,
        @NotBlank
        String confirmation
) {}
