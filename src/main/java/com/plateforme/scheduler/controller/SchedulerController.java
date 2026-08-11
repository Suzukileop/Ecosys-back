package com.plateforme.scheduler.controller;

import com.plateforme.scheduler.dto.ScheduledConfigDto;
import com.plateforme.scheduler.dto.ScheduledPostResponse;
import com.plateforme.scheduler.service.SchedulerEcosystemService;
import com.plateforme.shared.dto.PagedResponse;
import com.plateforme.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Client-facing scheduling: weekly time slots only.
 * Content creation and publishing are handled by a human agent — not manual client posts.
 */
@RestController
@RequestMapping("/api/scheduler")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Scheduler", description = "Weekly schedule configuration (client)")
@PreAuthorize("hasRole('CREATOR')")
public class SchedulerController {

    private final SchedulerEcosystemService schedulerEcosystemService;

    @Operation(summary = "Read weekly publication slots for a niche request")
    @GetMapping("/config/{requestId}")
    public ResponseEntity<ScheduledConfigDto> getConfig(@PathVariable UUID requestId) {
        UUID clientId = getCurrentUserId();
        return ResponseEntity.ok(schedulerEcosystemService.getMyScheduledConfig(clientId, requestId));
    }

    @Operation(summary = "Update weekly publication slots")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Config updated"),
            @ApiResponse(responseCode = "400", description = "Invalid data")
    })
    @PutMapping("/config/{requestId}")
    public ResponseEntity<ScheduledConfigDto> updateConfig(
            @PathVariable UUID requestId,
            @Valid @RequestBody ScheduledConfigDto body) {
        UUID clientId = getCurrentUserId();
        return ResponseEntity.ok(schedulerEcosystemService.updateScheduledConfig(clientId, requestId, body));
    }

    @Operation(summary = "List agent-delivered posts for a niche request")
    @GetMapping("/posts/niche/{requestId}")
    public ResponseEntity<PagedResponse<ScheduledPostResponse>> getNichePosts(
            @PathVariable UUID requestId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID clientId = getCurrentUserId();
        var pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 50));
        return ResponseEntity.ok(
                PagedResponse.fromPage(schedulerEcosystemService.getPostsByNicheRequest(clientId, requestId, pageable)));
    }

    private UUID getCurrentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
