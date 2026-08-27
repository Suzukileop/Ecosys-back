package com.plateforme.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Email(message = "Email invalide")
        @NotBlank(message = "L'email est obligatoire")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
        String password,

        @NotBlank(message = "Le nom complet est obligatoire")
        String fullName,

        @NotBlank(message = "Le nom d'utilisateur est obligatoire")
        @Size(min = 3, max = 30, message = "Le nom d'utilisateur doit contenir entre 3 et 30 caractères")
        @Pattern(
                regexp = "^[A-Za-z0-9_]+$",
                message = "Le nom d'utilisateur ne peut contenir que des lettres, chiffres et underscores"
        )
        String username,

        /** Ignored — all accounts are created as CREATOR. Kept for API compatibility. */
        String role
) {}
