package com.plateforme.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Email is required.")
        String email,

        @NotBlank(message = "Password is required.")
        String password,

        /** Interface language, used if a new verification e-mail has to be sent. Optional. */
        @Size(max = 16)
        String locale
) {
    public LoginRequest(String email, String password) {
        this(email, password, null);
    }
}
