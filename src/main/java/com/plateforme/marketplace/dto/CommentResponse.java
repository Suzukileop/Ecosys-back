package com.plateforme.marketplace.dto;

import com.plateforme.marketplace.entity.ContentTargetType;
import com.plateforme.marketplace.entity.ReactionType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        ContentTargetType targetType,
        UUID targetId,
        UUID userId,
        String userName,
        String userAvatarUrl,
        String comment,
        UUID parentId,
        LocalDateTime createdAt,
        long likes,
        long dislikes,
        ReactionType userReaction,
        boolean hidden,
        List<CommentResponse> replies
) {
    public CommentResponse {
        replies = replies != null ? replies : List.of();
    }
}
