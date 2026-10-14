package com.plateforme.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Email(message = "Enter a valid email address.")
        @NotBlank(message = "Email is required.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(min = 8, message = "Password must be at least 8 characters.")
        String password,

        @NotBlank(message = "Full name is required.")
        String fullName,

        @NotBlank(message = "Username is required.")
        @Size(min = 3, max = 30, message = "Username must be 3 to 30 characters.")
        @Pattern(
                regexp = "^[A-Za-z0-9_]+$",
                message = "Username may only contain letters, numbers and underscores."
        )
        String username,

        /** Ignored — all accounts are created as CREATOR. Kept for API compatibility. */
        String role,

        /** Interface language (e.g. "fr"), used for the verification e-mail. Optional. */
        @Size(max = 16)
        String locale
) {
    public SignupRequest(String email, String password, String fullName, String username, String role) {
        this(email, password, fullName, username, role, null);
    }
}
