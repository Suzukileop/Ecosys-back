package com.plateforme.ecosystem.controller;

import com.plateforme.ecosystem.dto.AgentDeliverContentDto;
import com.plateforme.ecosystem.dto.AgentProposeDto;
import com.plateforme.ecosystem.dto.ChatMessageDto;
import com.plateforme.ecosystem.dto.NicheRequestResponse;
import com.plateforme.ecosystem.service.AgentEcosystemService;
import com.plateforme.ecosystem.service.DeepSeekBotService;
import com.plateforme.scheduler.dto.ScheduledPostResponse;
import com.plateforme.scheduler.entity.Platform;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Agent", description = "Parcours agent — niche écosystème")
public class AgentController {

    private final AgentEcosystemService agentEcosystemService;
    private final DeepSeekBotService deepSeekBotService;

    @Operation(summary = "Lister les demandes niche en attente d'agent")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste paginée")
    })
    @GetMapping("/niche-requests")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public ResponseEntity<PagedResponse<NicheRequestResponse>> getPendingRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(PagedResponse.fromPage(agentEcosystemService.getPendingRequests(pageable)));
    }

    @Operation(summary = "Lister les niches actives (livraison de contenu)")
    @GetMapping("/niche-requests/active")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public ResponseEntity<PagedResponse<NicheRequestResponse>> getActiveNiches(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 50));
        return ResponseEntity.ok(PagedResponse.fromPage(agentEcosystemService.getActiveNiches(pageable)));
    }

    @Operation(summary = "Contenus livrés pour une niche active")
    @GetMapping("/niche-requests/{id}/delivered-content")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public ResponseEntity<PagedResponse<ScheduledPostResponse>> getDeliveredContent(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID agentId = getCurrentUserId();
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 50));
        return ResponseEntity.ok(
                PagedResponse.fromPage(agentEcosystemService.getDeliveredContent(id, agentId, pageable)));
    }

    @Operation(summary = "Détail demande pour l'agent")
    @GetMapping("/niche-requests/{id}")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public ResponseEntity<NicheRequestResponse> getRequestForAgent(@PathVariable UUID id) {
        return ResponseEntity.ok(agentEcosystemService.getRequestForAgent(id));
    }

    @Operation(summary = "Historique du chat bot client (lecture agent)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historique retourné"),
            @ApiResponse(responseCode = "404", description = "Demande introuvable")
    })
    @GetMapping("/niche-requests/{id}/bot-history")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public ResponseEntity<List<ChatMessageDto>> getBotHistoryForAgent(@PathVariable UUID id) {
        return ResponseEntity.ok(deepSeekBotService.getBotHistoryForAgent(id));
    }

    @Operation(summary = "Uploader le média de démonstration")
    @PostMapping(value = "/niche-requests/{id}/demo-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public ResponseEntity<Map<String, String>> uploadDemo(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file) {
        UUID agentId = getCurrentUserId();
        String url = agentEcosystemService.uploadDemoContent(id, agentId, file);
        return ResponseEntity.ok(Map.of("demoContentUrl", url));
    }

    @Operation(summary = "Uploader la vidéo modèle réutilisable (R2)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "URL publique R2 retournée"),
            @ApiResponse(responseCode = "404", description = "Demande introuvable")
    })
    @PostMapping(value = "/models/{requestId}/upload-video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public ResponseEntity<Map<String, String>> uploadModelVideo(
            @PathVariable UUID requestId,
            @RequestPart("file") MultipartFile file) {
        UUID agentId = getCurrentUserId();
        String url = agentEcosystemService.uploadModelVideo(requestId, agentId, file);
        return ResponseEntity.ok(Map.of("mediaUrl", url));
    }

    @Operation(summary = "Proposer le modèle de validation")
    @PutMapping("/niche-requests/{id}/propose")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public ResponseEntity<NicheRequestResponse> proposeModel(
            @PathVariable UUID id,
            @Valid @RequestBody AgentProposeDto dto) {
        UUID agentId = getCurrentUserId();
        return ResponseEntity.ok(agentEcosystemService.proposeModel(id, agentId, dto));
    }

    @Operation(summary = "Livrer un contenu au client pour une niche active")
    @PostMapping(value = "/niche-requests/{id}/deliver-content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public ResponseEntity<ScheduledPostResponse> deliverContent(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestParam Platform platform,
            @RequestParam(required = false) String caption) {
        UUID agentId = getCurrentUserId();
        AgentDeliverContentDto dto = new AgentDeliverContentDto(platform, caption);
        return ResponseEntity.ok(agentEcosystemService.deliverContent(id, agentId, file, dto));
    }

    private UUID getCurrentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
