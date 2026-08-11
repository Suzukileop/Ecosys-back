package com.plateforme.marketplace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ContentPostRequest(
        @Size(max = 300)
        String title,

        @Size(max = 100)
        String genre,

        @NotBlank
        @Size(max = 500)
        String mediaUrl,

        @Size(max = 20)
        String mediaType,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Invalid hex color")
        @Size(max = 20)
        String textColor,

        @Size(max = 100)
        String moodLabel,

        @Size(max = 20)
        String moodEmoji,

        List<UUID> taggedUserIds,

        @Size(max = 2000)
        String description,

        @Size(max = 200)
        String priceInfo,

        List<String> toolsUsed,

        List<String> tags,

        Boolean isPublic,

        Boolean commentsEnabled
) {
    public ContentPostRequest {
        if (isPublic == null) {
            isPublic = Boolean.TRUE;
        }
        if (commentsEnabled == null) {
            commentsEnabled = Boolean.TRUE;
        }
    }
}
