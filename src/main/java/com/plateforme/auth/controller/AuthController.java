package com.plateforme.auth.controller;

import com.plateforme.auth.dto.AuthResponse;
import com.plateforme.auth.dto.ForgotPasswordRequest;
import com.plateforme.auth.dto.LoginRequest;
import com.plateforme.auth.dto.ResendVerificationRequest;
import com.plateforme.auth.dto.ResendVerificationResponse;
import com.plateforme.auth.dto.ResetPasswordRequest;
import com.plateforme.auth.dto.SignupRequest;
import com.plateforme.auth.dto.SignupResponse;
import com.plateforme.auth.dto.VerifyEmailRequest;
import com.plateforme.auth.security.JwtUtils;
import com.plateforme.auth.security.RefreshTokenCookies;
import com.plateforme.auth.service.AuthService;
import com.plateforme.shared.util.LogMasking;
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
    private final RefreshTokenCookies refreshTokenCookies;

    @Operation(summary = "Inscription",
            description = "Crée un compte CREATOR verrouillé et envoie un code de vérification par e-mail. "
                    + "Aucune session n'est ouverte : elle commence à POST /api/auth/verify-email.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Compte créé, code envoyé"),
            @ApiResponse(responseCode = "400", description = "Données invalides ou email déjà utilisé"),
            @ApiResponse(responseCode = "429", description = "Trop de requêtes")
    })
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        log.info("Tentative d'inscription pour: {}", LogMasking.email(request.email()));
        SignupResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Vérifier l'e-mail",
            description = "Valide le code à 6 chiffres reçu par e-mail, déverrouille le compte et ouvre la session")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "E-mail vérifié, tokens retournés"),
            @ApiResponse(responseCode = "400", description = "Code incorrect, expiré ou trop d'essais"),
            @ApiResponse(responseCode = "429", description = "Trop de requêtes")
    })
    @PostMapping("/verify-email")
    public ResponseEntity<AuthResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return refreshTokenCookies.issue(authService.verifyEmail(request));
    }

    @Operation(summary = "Renvoyer le code",
            description = "Envoie un nouveau code si l'adresse a un compte non vérifié. Réponse identique dans tous les cas.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Demande prise en compte"),
            @ApiResponse(responseCode = "429", description = "Trop de codes demandés")
    })
    @PostMapping("/verify-email/resend")
    public ResponseEntity<ResendVerificationResponse> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(authService.resendVerification(request));
    }

    @Operation(summary = "Mot de passe oublié",
            description = "Envoie un code de réinitialisation si l'adresse a un compte actif. Réponse identique dans tous les cas.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Demande prise en compte"),
            @ApiResponse(responseCode = "429", description = "Trop de requêtes")
    })
    @PostMapping("/password/forgot")
    public ResponseEntity<ResendVerificationResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(authService.forgotPassword(request));
    }

    @Operation(summary = "Réinitialiser le mot de passe",
            description = "Vérifie le code reçu par e-mail, enregistre le nouveau mot de passe et ferme toutes les sessions.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Mot de passe modifié"),
            @ApiResponse(responseCode = "400", description = "Code incorrect, expiré ou trop d'essais"),
            @ApiResponse(responseCode = "429", description = "Trop de requêtes")
    })
    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Connexion", description = "Authentifie un utilisateur et retourne les tokens")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Connexion réussie"),
            @ApiResponse(responseCode = "401", description = "Identifiants incorrects"),
            @ApiResponse(responseCode = "429", description = "Trop de requêtes")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Tentative de connexion pour: {}", LogMasking.email(request.email()));
        AuthResponse response = authService.login(request);
        return refreshTokenCookies.issue(response);
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
        return refreshTokenCookies.issue(response);
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
        return ResponseEntity.noContent()
                .header(org.springframework.http.HttpHeaders.SET_COOKIE, refreshTokenCookies.clearHeader())
                .build();
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (headerAuth != null && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}
