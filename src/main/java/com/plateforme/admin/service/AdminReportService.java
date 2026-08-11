package com.plateforme.admin.service;

import com.plateforme.admin.dto.UpdateReportRequest;
import com.plateforme.marketplace.dto.ReportResponse;
import com.plateforme.marketplace.entity.ContentReport;
import com.plateforme.marketplace.entity.ReportStatus;
import com.plateforme.marketplace.repository.ContentReportRepository;
import com.plateforme.marketplace.service.MarketplaceSocialService;
import com.plateforme.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminReportService {

    private final ContentReportRepository reportRepository;
    private final MarketplaceSocialService marketplaceSocialService;

    @Transactional(readOnly = true)
    public Page<ReportResponse> listReports(ReportStatus status, Pageable pageable) {
        ReportStatus filter = status != null ? status : ReportStatus.PENDING;
        return reportRepository.findByStatusOrderByCreatedAtDesc(filter, pageable)
                .map(marketplaceSocialService::toReportResponse);
    }

    @Transactional
    public ReportResponse updateReport(UUID reportId, UpdateReportRequest request) {
        ContentReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException("REPORT_NOT_FOUND", "Report not found: " + reportId));

        if (request.status() != null) {
            report.setStatus(request.status());
        }
        if (request.adminNotes() != null) {
            report.setAdminNotes(request.adminNotes().isBlank() ? null : request.adminNotes().trim());
        }

        report = reportRepository.save(report);
        log.info("Report updated id={} status={}", reportId, report.getStatus());
        return marketplaceSocialService.toReportResponse(report);
    }
}
