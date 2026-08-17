package com.plateforme.user.presence;

import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/presence")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Presence", description = "Online / offline user presence")
@SecurityRequirement(name = "bearerAuth")
public class PresenceController {

    private static final int MAX_IDS = 50;

    private final PresenceService presenceService;

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Batch presence status for user ids (max 50)")
    public ResponseEntity<List<PresenceStatusDto>> getStatuses(
            @RequestParam("ids") String idsParam) {
        List<UUID> ids = parseIds(idsParam);
        Map<UUID, PresenceStatus> statuses = presenceService.getStatuses(ids);
        List<PresenceStatusDto> body = new ArrayList<>(statuses.size());
        for (UUID id : ids) {
            PresenceStatus status = statuses.get(id);
            if (status != null) {
                body.add(PresenceStatusDto.from(status));
            }
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/heartbeat")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark the current user online (dashboard heartbeat)")
    public ResponseEntity<PresenceStatusDto> heartbeat() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        PresenceStatus status = presenceService.heartbeat(user.getId());
        return ResponseEntity.ok(PresenceStatusDto.from(status));
    }

    private static List<UUID> parseIds(String idsParam) {
        if (idsParam == null || idsParam.isBlank()) {
            return List.of();
        }
        String[] parts = idsParam.split(",");
        LinkedHashSet<UUID> unique = new LinkedHashSet<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                unique.add(UUID.fromString(trimmed));
            } catch (IllegalArgumentException ex) {
                throw new BusinessException("PRESENCE_INVALID_ID", "Invalid user id: " + trimmed);
            }
            if (unique.size() > MAX_IDS) {
                throw new BusinessException(
                        "PRESENCE_TOO_MANY_IDS",
                        "At most " + MAX_IDS + " user ids are allowed");
            }
        }
        return new ArrayList<>(unique);
    }
}
