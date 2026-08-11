package com.plateforme.ecosystem.controller;

import com.plateforme.ecosystem.dto.NicheRequestFormDto;
import com.plateforme.ecosystem.dto.NicheRequestResponse;
import com.plateforme.ecosystem.service.EcosystemService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ecosystem")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Ecosystem", description = "Demandes niche — client")
public class EcosystemController {

    private final EcosystemService ecosystemService;

    @Operation(summary = "Soumettre une demande de niche")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Demande créée"),
            @ApiResponse(responseCode = "400", description = "Données invalides"),
            @ApiResponse(responseCode = "403", description = "Accès refusé")
    })
    @PostMapping("/niche-request")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<NicheRequestResponse> submitNicheRequest(
            @Valid @RequestBody NicheRequestFormDto dto) {
        UUID clientId = getCurrentUserId();
        NicheRequestResponse body = ecosystemService.submitNicheRequest(clientId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Uploader une vidéo de référence MP4")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fichier stocké"),
            @ApiResponse(responseCode = "400", description = "Fichier invalide")
    })
    @PostMapping(value = "/niche-request/{id}/ref-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<Map<String, String>> uploadRefFile(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file) {
        UUID clientId = getCurrentUserId();
        String url = ecosystemService.uploadRefFile(clientId, id, file);
        return ResponseEntity.ok(Map.of("refFileUrl", url));
    }

    @Operation(summary = "Lister mes demandes niche")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste paginée")
    })
    @GetMapping("/my-requests")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<PagedResponse<NicheRequestResponse>> getMyRequests(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID clientId = getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(PagedResponse.fromPage(ecosystemService.getMyRequests(clientId, status, pageable)));
    }

    @Operation(summary = "Détail d'une demande")
    @GetMapping("/my-requests/{id}")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<NicheRequestResponse> getRequestDetail(@PathVariable UUID id) {
        UUID clientId = getCurrentUserId();
        return ResponseEntity.ok(ecosystemService.getRequestDetail(id, clientId));
    }

    @Operation(summary = "Annuler une demande")
    @DeleteMapping("/my-requests/{id}")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<Void> cancelRequest(@PathVariable UUID id) {
        UUID clientId = getCurrentUserId();
        ecosystemService.cancelRequest(id, clientId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Confirmer la niche après l’assistant et passer en attente d’agent")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Demande mise à jour"),
            @ApiResponse(responseCode = "400", description = "État invalide")
    })
    @PostMapping("/my-requests/{id}/confirm-bot-chat")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<NicheRequestResponse> confirmBotChat(@PathVariable UUID id) {
        UUID clientId = getCurrentUserId();
        return ResponseEntity.ok(ecosystemService.confirmBotChat(id, clientId));
    }

    private UUID getCurrentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
