package com.plateforme.scheduler.dto;

import com.plateforme.scheduler.entity.ContentType;
import com.plateforme.scheduler.entity.Platform;
import com.plateforme.scheduler.entity.PostStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ScheduledPostResponse(
        UUID id,
        Platform platform,
        String contentUrl,
        ContentType contentType,
        String caption,
        String nicheRef,
        Integer deliveryNumber,
        LocalDateTime scheduledAt,
        PostStatus status,
        LocalDateTime publishedAt,
        String errorMessage,
        LocalDateTime createdAt
) {}
