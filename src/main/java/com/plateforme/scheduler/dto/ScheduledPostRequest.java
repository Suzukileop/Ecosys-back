package com.plateforme.scheduler.dto;

import com.plateforme.scheduler.entity.ContentType;
import com.plateforme.scheduler.entity.Platform;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record ScheduledPostRequest(
        @NotNull(message = "A niche request is required.")
        UUID nicheRequestId,

        @NotNull(message = "A platform is required.")
        Platform platform,

        String contentUrl,

        ContentType contentType,

        @Size(max = 2200)
        String caption,

        @NotNull(message = "A publish date is required.")
        @Future(message = "The publish date must be in the future.")
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

