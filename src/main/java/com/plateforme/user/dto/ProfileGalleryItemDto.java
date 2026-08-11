package com.plateforme.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ProfileGalleryItemDto(
        UUID id,
        int sortOrder,
        @Size(max = 120)
        String title,
        @NotBlank
        String mediaUrl,
        @Pattern(regexp = "IMAGE|VIDEO")
        String mediaType
) {}
