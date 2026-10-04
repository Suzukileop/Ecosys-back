package com.plateforme.marketplace.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ContentPostResponse(
        UUID id,
        String title,
        String genre,
        String mediaUrl,
        String mediaType,
        String textColor,
        String moodLabel,
        String moodEmoji,
        List<MinimalUserDto> taggedUsers,
        String description,
        String priceInfo,
        List<String> toolsUsed,
        List<String> tags,
        boolean isPublic,
        boolean commentsEnabled,
        boolean pinned,
        LocalDateTime archivedAt,
        int views,
        int likes,
        long portfolioCount,
        LocalDateTime createdAt,
        MinimalUserDto creator,
        /**
         * Supplied by the public feed so a card does not have to fetch them one by one.
         * {@code null} on routes that do not compute them — the client then falls back to its
         * own request rather than rendering a wrong zero.
         */
        Long commentCount,
        String viewerReaction
) {}
