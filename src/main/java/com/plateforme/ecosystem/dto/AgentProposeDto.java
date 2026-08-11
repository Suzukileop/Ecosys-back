package com.plateforme.ecosystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentProposeDto(
        @NotBlank String demoContentUrl,
        @Size(max = 1000) String agentNotes
) {
}
