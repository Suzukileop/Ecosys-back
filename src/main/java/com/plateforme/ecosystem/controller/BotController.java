package com.plateforme.ecosystem.controller;

import com.plateforme.ecosystem.dto.BotMessageDto;
import com.plateforme.ecosystem.dto.BotResponseDto;
import com.plateforme.ecosystem.dto.ChatMessageDto;
import com.plateforme.auth.security.CurrentUserUtil;
import com.plateforme.ecosystem.service.DeepSeekBotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ecosystem/bot")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Ecosystem Bot", description = "Assistant DeepSeek pour confirmation de niche")
@PreAuthorize("hasRole('CREATOR')")
public class BotController {

    private final DeepSeekBotService deepSeekBotService;

    @Operation(summary = "Envoyer un message au bot niche")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réponse bot"),
            @ApiResponse(responseCode = "503", description = "Bot indisponible")
    })
    @PostMapping("/{requestId}/message")
    public ResponseEntity<BotResponseDto> sendMessage(
            @PathVariable UUID requestId,
            @Valid @RequestBody BotMessageDto dto) {
        UUID clientId = getCurrentUserId();
        BotResponseDto body = deepSeekBotService.sendBotMessage(requestId, clientId, dto.message());
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "Historique du chat bot pour une demande")
    @GetMapping("/{requestId}/history")
    public ResponseEntity<List<ChatMessageDto>> getHistory(@PathVariable UUID requestId) {
        UUID clientId = getCurrentUserId();
        return ResponseEntity.ok(deepSeekBotService.getBotHistory(requestId, clientId));
    }

    private UUID getCurrentUserId() {
        return CurrentUserUtil.requireUserFromAuthentication(
                SecurityContextHolder.getContext().getAuthentication()).getId();
    }
}
