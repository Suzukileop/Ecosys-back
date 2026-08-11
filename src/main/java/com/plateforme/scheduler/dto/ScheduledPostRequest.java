package com.plateforme.scheduler.dto;

import com.plateforme.scheduler.entity.ContentType;
import com.plateforme.scheduler.entity.Platform;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record ScheduledPostRequest(
        @NotNull(message = "Le demande niche est obligatoire")
        UUID nicheRequestId,

        @NotNull(message = "La plateforme est obligatoire")
        Platform platform,

        String contentUrl,

        ContentType contentType,

        @Size(max = 2200)
        String caption,

        @NotNull(message = "La date de publication est obligatoire")
        @Future(message = "La date doit être dans le futur")
        LocalDateTime scheduledAt,

        @Size(max = 20)
        String nicheRef
) {
    public ScheduledPostRequest {
        if (contentType == null) {
            contentType = ContentType.EXTERNAL_URL;
        }
    }
}

