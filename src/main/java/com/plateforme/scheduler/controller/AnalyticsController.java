package com.plateforme.scheduler.controller;

import com.plateforme.scheduler.dto.AnalyticsDashboardResponse;
import com.plateforme.scheduler.dto.CreatorAnalyticsResponse;
import com.plateforme.scheduler.service.AnalyticsService;
import com.plateforme.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Analytics", description = "Statistiques publications et espace créateur")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @Operation(summary = "Tableau de bord client (publications planifiées)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statistiques agrégées"),
            @ApiResponse(responseCode = "403", description = "Accès refusé")
    })
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('CREATOR','ADMIN')")
    public ResponseEntity<AnalyticsDashboardResponse> getDashboard(
            @Parameter(description = "Identifiant client (admin uniquement)")
            @RequestParam(required = false) UUID clientId) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID targetClientId = resolveTargetClientId(user, clientId);
        return ResponseEntity.ok(analyticsService.getDashboard(targetClientId));
    }

    @Operation(summary = "Statistiques agrégées pour un créateur")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Totaux contenus"),
            @ApiResponse(responseCode = "403", description = "Accès refusé")
    })
    @GetMapping("/creator")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<CreatorAnalyticsResponse> getCreatorAnalytics() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(analyticsService.getCreatorAnalytics(user.getId()));
    }

    private UUID resolveTargetClientId(User user, UUID queryClientId) {
        boolean isAdmin = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        if (isAdmin && queryClientId != null) {
            return queryClientId;
        }
        return user.getId();
    }
}
