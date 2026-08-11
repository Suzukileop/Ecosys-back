package com.plateforme.admin.controller;

import com.plateforme.admin.service.AdminCreditService;
import com.plateforme.credits.dto.CreditBalanceResponse;
import com.plateforme.credits.dto.SetCreditBalanceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration — Crédits", description = "Gestion des soldes crédits (tests / support)")
@SecurityRequirement(name = "bearerAuth")
public class AdminCreditController {

    private final AdminCreditService adminCreditService;

    @Operation(summary = "Consulter le solde crédits d'un utilisateur")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solde retourné"),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable")
    })
    @GetMapping("/{id}/credits")
    public ResponseEntity<CreditBalanceResponse> getCredits(@PathVariable UUID id) {
        return ResponseEntity.ok(adminCreditService.getBalance(id));
    }

    @Operation(summary = "Fixer le solde crédits d'un utilisateur", description = "Remplace le solde (ex. 500 pour les tests pipeline IA)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solde mis à jour"),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable")
    })
    @PutMapping("/{id}/credits")
    public ResponseEntity<CreditBalanceResponse> setCredits(
            @PathVariable UUID id,
            @Valid @RequestBody SetCreditBalanceRequest request) {
        return ResponseEntity.ok(adminCreditService.setBalance(id, request.balance()));
    }
}
