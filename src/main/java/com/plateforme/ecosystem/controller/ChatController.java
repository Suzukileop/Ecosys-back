package com.plateforme.ecosystem.controller;

import com.plateforme.ecosystem.dto.ChatMessageDto;
import com.plateforme.ecosystem.dto.SendMessageDto;
import com.plateforme.ecosystem.entity.ChatMessage;
import com.plateforme.auth.security.CurrentUserUtil;
import com.plateforme.ecosystem.service.ChatService;
import com.plateforme.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Chat", description = "Messagerie en temps réel par room WebSocket")
public class ChatController {

    private final ChatService chatService;

    /**
     * Convention room_id : niche-{serviceRequestUniqueCode} (ex: niche-MCT-A1B2)
     */
    @MessageMapping("/chat/{roomId}/send")
    @SendTo("/topic/chat/{roomId}")
    public ChatMessageDto sendMessage(
            @DestinationVariable String roomId,
            @Valid SendMessageDto dto,
            Principal principal) {

        if (principal == null) {
            log.warn("Tentative d'envoi de message sans authentification dans la room={}", roomId);
            throw new IllegalStateException("Authentification requise pour envoyer un message");
        }

        User user = CurrentUserUtil.requireUser(principal);
        ChatMessage saved = chatService.saveMessage(roomId, user.getId(), dto.content());

        log.debug("Message diffusé dans room={} par user={}", roomId, user.getId());

        return new ChatMessageDto(
                saved.getId(),
                saved.getRoomId(),
                saved.getSender() != null ? saved.getSender().getId() : null,
                saved.getSender() != null ? saved.getSender().getFullName() : "Bot",
                saved.getContent(),
                saved.getSentAt(),
                saved.getIsRead(),
                saved.getSenderType() != null ? saved.getSenderType().name() : "HUMAN"
        );
    }

    @Operation(summary = "Récupérer l'historique d'une room de chat")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historique retourné"),
            @ApiResponse(responseCode = "401", description = "Non authentifié")
    })
    @GetMapping("/api/chat/{roomId}/history")
    @PreAuthorize("isAuthenticated()")
    @ResponseBody
    public ResponseEntity<Page<ChatMessageDto>> getChatHistory(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(chatService.getHistory(roomId, pageable));
    }
}
