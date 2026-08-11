package com.plateforme.ecosystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageDto(
        @NotBlank @Size(max = 2000)
        String content
) {}
