package com.plateforme.user.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreatorProfileVisitItemDto(
        UUID id,
        LocalDateTime viewedAt,
        boolean anonymous,
        UUID viewerUserId,
        String viewerFullName,
        String viewerAvatarUrl,
        String viewerAppRole,
        int visitCount
) {}
