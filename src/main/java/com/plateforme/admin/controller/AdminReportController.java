package com.plateforme.admin.controller;

import com.plateforme.admin.dto.UpdateReportRequest;
import com.plateforme.admin.service.AdminReportService;
import com.plateforme.marketplace.dto.ReportResponse;
import com.plateforme.marketplace.entity.ReportStatus;
import com.plateforme.shared.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Reports", description = "Content report moderation")
@SecurityRequirement(name = "bearerAuth")
public class AdminReportController {

    private final AdminReportService adminReportService;

    @Operation(summary = "List content reports by status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reports returned")
    })
    @GetMapping
    public ResponseEntity<PagedResponse<ReportResponse>> listReports(
            @RequestParam(defaultValue = "PENDING") ReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(PagedResponse.fromPage(adminReportService.listReports(status, pageable)));
    }

    @Operation(summary = "Update report status and admin notes")
    @PatchMapping("/{id}")
    public ResponseEntity<ReportResponse> updateReport(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateReportRequest request) {
        return ResponseEntity.ok(adminReportService.updateReport(id, request));
    }
}
