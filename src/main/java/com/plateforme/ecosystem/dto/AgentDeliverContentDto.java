package com.plateforme.ecosystem.dto;

import com.plateforme.scheduler.entity.Platform;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AgentDeliverContentDto(
        @NotNull(message = "La plateforme est obligatoire")
        Platform platform,

        @Size(max = 2200)
        String caption
) {}
