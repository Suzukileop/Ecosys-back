package com.plateforme.auth.controller;

import com.plateforme.auth.dto.AuthResponse;
import com.plateforme.auth.dto.LoginRequest;
import com.plateforme.auth.dto.SignupRequest;
import com.plateforme.auth.security.JwtUtils;
import com.plateforme.auth.service.AuthService;
import com.plateforme.user.entity.User;
import com.plateforme.user.presence.PresenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentification", description = "API d'authentification et gestion des tokens")
public class AuthController {

    private final AuthService authService;
    private final JwtUtils jwtUtils;
    private final PresenceService presenceService;

    @Operation(summary = "Inscription", description = "Crée un nouveau compte utilisateur (CREATOR)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Compte créé avec succès"),
            @ApiResponse(responseCode = "400", description = "Données invalides ou email déjà utilisé"),
            @ApiResponse(responseCode = "429", description = "Trop de requêtes")
    })
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        log.info("Tentative d'inscription pour: {}", request.email());
        AuthResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Connexion", description = "Authentifie un utilisateur et retourne les tokens")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Connexion réussie"),
            @ApiResponse(responseCode = "401", description = "Identifiants incorrects"),
            @ApiResponse(responseCode = "429", description = "Trop de requêtes")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Tentative de connexion pour: {}", request.email());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Renouveler le token", description = "Génère un nouvel access token à partir du refresh token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token renouvelé"),
            @ApiResponse(responseCode = "401", description = "Refresh token manquant"),
            @ApiResponse(responseCode = "400", description = "Refresh token invalide, révoqué ou expiré")
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AuthResponse response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Déconnexion", description = "Révoque le refresh token et blackliste l'access token courant")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Déconnexion réussie"),
            @ApiResponse(responseCode = "401", description = "Non authentifié")
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest httpRequest,
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            @AuthenticationPrincipal UserDetails userDetails) {

        String jwt = extractJwtFromRequest(httpRequest);
        String jti = null;
        long remainingMs = 0;

        if (jwt != null && jwtUtils.validateToken(jwt)) {
            jti = jwtUtils.extractJti(jwt);
            Date expiration = jwtUtils.extractExpiration(jwt);
            remainingMs = expiration.getTime() - System.currentTimeMillis();
        }

        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken, jti, remainingMs);
        }
        if (userDetails instanceof User user) {
            try {
                presenceService.forceOffline(user.getId());
            } catch (Exception ex) {
                log.warn("Presence offline on logout failed: {}", ex.getMessage());
            }
        }
        log.info("Déconnexion effectuée pour: {}", userDetails != null ? userDetails.getUsername() : "unknown");
        return ResponseEntity.noContent().build();
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (headerAuth != null && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}
