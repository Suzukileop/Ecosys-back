package com.plateforme.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendDirectMessageDto(
        @NotBlank @Size(max = 2000)
        String content
) {
}
