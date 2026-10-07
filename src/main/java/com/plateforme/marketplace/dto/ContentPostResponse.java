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
        String viewerReaction,
        /** Full gallery; always holds the cover when the post has media, empty for text posts. */
        List<String> mediaUrls,
        /** The original when this post is a repost ({@code null} otherwise, and never nested twice). */
        ContentPostResponse repostOf,
        long repostCount,
        /** Viewer-specific flags — {@code null} on routes that do not compute them. */
        Boolean viewerReposted,
        Boolean viewerSaved
) {
    /** Payloads that carry none of the gallery / repost / save state (portfolio works, tests). */
    public ContentPostResponse(
            UUID id, String title, String genre, String mediaUrl, String mediaType, String textColor,
            String moodLabel, String moodEmoji, List<MinimalUserDto> taggedUsers, String description,
            String priceInfo, List<String> toolsUsed, List<String> tags, boolean isPublic,
            boolean commentsEnabled, boolean pinned, LocalDateTime archivedAt, int views, int likes,
            long portfolioCount, LocalDateTime createdAt, MinimalUserDto creator, Long commentCount,
            String viewerReaction) {
        this(id, title, genre, mediaUrl, mediaType, textColor, moodLabel, moodEmoji, taggedUsers,
                description, priceInfo, toolsUsed, tags, isPublic, commentsEnabled, pinned, archivedAt,
                views, likes, portfolioCount, createdAt, creator, commentCount, viewerReaction,
                mediaUrl != null && !mediaUrl.isBlank() ? List.of(mediaUrl) : List.of(),
                null, 0L, null, null);
    }
}
