package com.plateforme.auth.controller;

import com.plateforme.auth.dto.AuthResponse;
import com.plateforme.auth.dto.OAuthCompleteRegistrationRequest;
import com.plateforme.auth.dto.OAuthExchangeRequest;
import com.plateforme.auth.dto.OAuthPendingProfileResponse;
import com.plateforme.auth.service.OAuthService;
import com.plateforme.shared.exception.BusinessException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/oauth")
@RequiredArgsConstructor
@Slf4j
public class OAuthController {

    private final OAuthService oauthService;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> status() {
        return ResponseEntity.ok(Map.of("google", oauthService.isGoogleEnabled()));
    }

    @GetMapping("/google")
    public void google(
            @RequestParam(defaultValue = "false") boolean signup,
            @RequestParam(defaultValue = "CREATOR") String role,
            HttpServletResponse response
    ) throws IOException {
        response.sendRedirect(oauthService.buildGoogleAuthorizationUrl(signup, role));
    }

    @GetMapping("/google/callback")
    public void googleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletResponse response
    ) throws IOException {
        if (error != null) {
            response.sendRedirect(oauthService.buildErrorRedirect("Google sign-in was cancelled"));
            return;
        }
        try {
            response.sendRedirect(oauthService.handleGoogleCallback(code, state));
        } catch (BusinessException ex) {
            log.warn("Google OAuth failed: {}", ex.getMessage());
            response.sendRedirect(oauthService.buildErrorRedirect(ex.getMessage()));
        }
    }

    @PostMapping("/exchange")
    public ResponseEntity<AuthResponse> exchange(@Valid @RequestBody OAuthExchangeRequest request) {
        return ResponseEntity.ok(oauthService.exchangeCode(request.code()));
    }

    @GetMapping("/pending-registration")
    public ResponseEntity<OAuthPendingProfileResponse> pendingRegistration(@RequestParam String code) {
        return ResponseEntity.ok(oauthService.getPendingRegistration(code));
    }

    @PostMapping("/complete-registration")
    public ResponseEntity<AuthResponse> completeRegistration(
            @Valid @RequestBody OAuthCompleteRegistrationRequest request
    ) {
        return ResponseEntity.ok(oauthService.completeRegistration(request.code()));
    }
}
