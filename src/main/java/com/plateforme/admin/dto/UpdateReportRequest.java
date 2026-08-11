package com.plateforme.admin.dto;

import com.plateforme.marketplace.entity.ReportStatus;
import jakarta.validation.constraints.Size;

public record UpdateReportRequest(
        ReportStatus status,
        @Size(max = 5000) String adminNotes
) {
}
