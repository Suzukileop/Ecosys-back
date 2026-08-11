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
        MinimalUserDto creator
) {}
