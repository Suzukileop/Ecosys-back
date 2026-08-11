package com.plateforme.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateUserProfileDto(
        @Size(max = 150)
        String fullName
) {}
