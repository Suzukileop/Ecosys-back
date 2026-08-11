package com.plateforme.scheduler.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ScheduledConfigDto(
        @NotNull UUID nicheRequestId,
        @NotEmpty @Valid List<PublicationSlotDto> publicationSlots
) {
}
