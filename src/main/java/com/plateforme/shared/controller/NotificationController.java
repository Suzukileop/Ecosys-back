package com.plateforme.shared.controller;

import com.plateforme.shared.dto.NotificationDto;
import com.plateforme.shared.service.NotificationService;
import com.plateforme.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notifications", description = "Gestion des notifications en temps réel")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "Récupérer mes notifications",
            description = "Retourne les notifications paginées, non lues en premier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste retournée"),
            @ApiResponse(responseCode = "401", description = "Non authentifié")
    })
    @GetMapping
    public ResponseEntity<Page<NotificationDto>> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(notificationService.getMyNotifications(userId, pageable));
    }

    @Operation(summary = "Compter les notifications non lues",
            description = "Retourne le nombre de notifications non lues de l'utilisateur connecté.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Compteur retourné"),
            @ApiResponse(responseCode = "401", description = "Non authentifié")
    })
    @GetMapping("/unread-count")
    public ResponseEntity<Long> countUnread() {
        UUID userId = getCurrentUserId();
        return ResponseEntity.ok(notificationService.countUnread(userId));
    }

    @Operation(summary = "Marquer une notification comme lue")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Marquée comme lue"),
            @ApiResponse(responseCode = "403", description = "Notification d'un autre utilisateur"),
            @ApiResponse(responseCode = "404", description = "Notification introuvable")
    })
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID id) {
        UUID userId = getCurrentUserId();
        notificationService.markAsRead(id, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Marquer toutes les notifications comme lues")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Toutes marquées comme lues"),
            @ApiResponse(responseCode = "401", description = "Non authentifié")
    })
    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        UUID userId = getCurrentUserId();
        notificationService.markAllAsRead(userId);
        return ResponseEntity.noContent().build();
    }

    private UUID getCurrentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
