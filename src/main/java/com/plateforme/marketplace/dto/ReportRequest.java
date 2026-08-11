package com.plateforme.marketplace.dto;

import com.plateforme.marketplace.entity.ContentTargetType;
import com.plateforme.marketplace.entity.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ReportRequest(
        @NotNull ContentTargetType targetType,
        @NotNull UUID targetId,
        @NotNull ReportReason reason,
        @Size(max = 5000) String details
) {
}
