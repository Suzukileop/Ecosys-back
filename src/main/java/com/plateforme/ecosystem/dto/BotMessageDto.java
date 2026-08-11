package com.plateforme.ecosystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record BotMessageDto(
        UUID nicheRequestId,
        @NotBlank @Size(max = 2000) String message
) {
}
