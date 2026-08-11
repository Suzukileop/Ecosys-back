package com.plateforme.marketplace.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendCreatorContactMessageRequest(
        @NotBlank
        @Size(max = 120)
        String name,

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @Size(max = 160)
        String subject,

        @NotBlank
        @Size(max = 4000)
        String message
) {
}
