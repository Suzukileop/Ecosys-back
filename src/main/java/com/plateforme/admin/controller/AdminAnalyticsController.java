package com.plateforme.admin.controller;

import com.plateforme.admin.dto.AdminGlobalStatsResponse;
import com.plateforme.admin.service.AdminAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Analytics", description = "Statistiques globales (admin)")
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;

    @Operation(summary = "Statistiques globales de la plateforme")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Totaux globaux"),
            @ApiResponse(responseCode = "403", description = "Accès refusé")
    })
    @GetMapping("/global")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminGlobalStatsResponse> getGlobalStats() {
        return ResponseEntity.ok(adminAnalyticsService.getGlobalStats());
    }
}
