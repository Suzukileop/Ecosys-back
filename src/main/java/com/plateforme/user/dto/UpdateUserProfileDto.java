package com.plateforme.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileDto(
        @Size(max = 150)
        String fullName,

        @Size(min = 3, max = 30, message = "Username must be 3–30 characters")
        @Pattern(
                regexp = "^[A-Za-z0-9_]+$",
                message = "Username may only contain letters, numbers, and underscores"
        )
        String username
) {}
