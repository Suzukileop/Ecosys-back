package com.plateforme.marketplace.dto;

import com.plateforme.marketplace.entity.ContentTargetType;
import com.plateforme.marketplace.entity.ReportReason;
import com.plateforme.marketplace.entity.ReportStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReportResponse(
        UUID id,
        ContentTargetType targetType,
        UUID targetId,
        UUID reporterId,
        ReportReason reason,
        String details,
        ReportStatus status,
        String adminNotes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
