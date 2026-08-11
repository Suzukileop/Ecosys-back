package com.plateforme.user.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreatorFollowItemDto(
        UUID id,
        LocalDateTime followedAt,
        UUID followerUserId,
        String followerFullName,
        String followerAvatarUrl
) {}
