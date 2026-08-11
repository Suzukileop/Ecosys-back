package com.plateforme.ecosystem.controller;

import com.plateforme.ecosystem.dto.NicheRequestResponse;
import com.plateforme.ecosystem.dto.ValidateModelDto;
import com.plateforme.ecosystem.service.ValidationService;
import com.plateforme.user.entity.User;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/ecosystem")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Ecosystem Validation", description = "Validation du modèle par le client")
@PreAuthorize("hasRole('CREATOR')")
public class ValidationController {

    private final ValidationService validationService;

    @Operation(summary = "Valider ou refuser le modèle proposé par l'agent")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Décision enregistrée"),
            @ApiResponse(responseCode = "400", description = "Statut ou données invalides")
    })
    @PutMapping("/niche-requests/{id}/validate")
    public ResponseEntity<NicheRequestResponse> validateModel(
            @PathVariable UUID id,
            @Valid @RequestBody ValidateModelDto dto) {
        UUID clientId = getCurrentUserId();
        return ResponseEntity.ok(validationService.validateModel(id, clientId, dto));
    }

    @Operation(summary = "Ignorer la validation modèle et passer au paiement")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Étape ignorée — paiement disponible"),
            @ApiResponse(responseCode = "400", description = "Statut invalide")
    })
    @PostMapping("/my-requests/{id}/skip-model-validation")
    public ResponseEntity<NicheRequestResponse> skipModelValidation(@PathVariable UUID id) {
        UUID clientId = getCurrentUserId();
        return ResponseEntity.ok(validationService.skipModelValidation(id, clientId));
    }

    private UUID getCurrentUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }
}
