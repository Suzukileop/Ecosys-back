package com.plateforme.credits.controller;

import com.plateforme.credits.dto.MyCreditBalanceResponse;
import com.plateforme.credits.service.CreditService;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/credits")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Crédits", description = "Solde crédits de l'utilisateur connecté")
@SecurityRequirement(name = "bearerAuth")
public class CreditController {

    private final CreditService creditService;
    private final UserRepository userRepository;

    @Operation(summary = "Consulter mon solde crédits")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solde retourné"),
            @ApiResponse(responseCode = "401", description = "Non authentifié")
    })
    @GetMapping("/balance")
    public ResponseEntity<MyCreditBalanceResponse> getMyBalance() {
        UUID userId = resolveCurrentUserId(SecurityContextHolder.getContext().getAuthentication());
        int balance = creditService.getBalance(userId);
        return ResponseEntity.ok(new MyCreditBalanceResponse(balance));
    }

    private UUID resolveCurrentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException("UNAUTHORIZED", "Non authentifié");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            return user.getId();
        }
        return userRepository.findByEmailAndDeletedAtIsNull(authentication.getName())
                .map(User::getId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND",
                        "Utilisateur introuvable : " + authentication.getName()));
    }
}
