package com.plateforme.scheduler.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Un créneau de publication : jour de la semaine (même échelle 0–6 que l’ancien {@code publication_days}) + heure HH:mm.
 */
public record PublicationSlotDto(
        @Min(0) @Max(6) int dayOfWeek,
        @NotNull @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$") String time
) {
}
