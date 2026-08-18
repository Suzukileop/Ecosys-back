package com.plateforme.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plateforme.auth.config.OAuthProperties;
import com.plateforme.auth.dto.AuthResponse;
import com.plateforme.auth.dto.OAuthPendingProfileResponse;
import com.plateforme.auth.dto.OAuthProfilePayload;
import com.plateforme.auth.security.JwtUtils;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.entity.CreatorProfile;
import com.plateforme.user.entity.RefreshToken;
import com.plateforme.user.entity.Role;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.RefreshTokenRepository;
import com.plateforme.user.repository.RoleRepository;
import com.plateforme.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthService {

    private final OAuthProperties oauthProperties;
    private final OAuthStateService oauthStateService;
    private final AuthService authService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    @Value("${app.jwt.refresh-expiration}")
    private long refreshExpirationMs;

    @Value("${app.jwt.expiration}")
    private long jwtExpirationMs;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public boolean isGoogleEnabled() {
        return oauthProperties.isGoogleConfigured();
    }

    public String buildGoogleAuthorizationUrl(boolean signup, String role) {
        ensureGoogleConfigured();
        String state = signup
                ? oauthStateService.createSignupState()
                : oauthStateService.createLoginState(role);
        OAuthProperties.Provider google = oauthProperties.getGoogle();
        return UriComponentsBuilder
                .fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", google.getClientId())
                .queryParam("redirect_uri", google.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .queryParam("prompt", "select_account")
                .build()
                .encode()
                .toUriString();
    }

    public String handleGoogleCallback(String code, String state) {
        ensureGoogleConfigured();
        String stateValue = oauthStateService.consumeState(state)
                .orElseThrow(() -> new BusinessException("OAUTH_STATE_INVALID", "Invalid OAuth state"));
        OAuthProperties.Provider google = oauthProperties.getGoogle();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", google.getClientId());
        form.add("client_secret", google.getClientSecret());
        form.add("redirect_uri", google.getRedirectUri());
        form.add("grant_type", "authorization_code");

        Map<String, Object> tokenResponse = postForm("https://oauth2.googleapis.com/token", form);

        String accessToken = asString(tokenResponse.get("access_token"));
        if (accessToken == null) {
            throw new BusinessException("OAUTH_TOKEN_ERROR", "Google token exchange failed");
        }

        Map<String, Object> profile = getJson(
                "https://www.googleapis.com/oauth2/v3/userinfo",
                accessToken
        );

        OAuthProfilePayload oauthProfile = new OAuthProfilePayload(
                "GOOGLE",
                required(profile.get("sub"), "Google profile id missing"),
                required(profile.get("email"), "Google account email is required"),
                optionalString(profile.get("name")),
                optionalString(profile.get("picture"))
        );

        return completeOAuthLogin(oauthProfile, stateValue);
    }

    public AuthResponse exchangeCode(String code) {
        String payload = oauthStateService.consumeExchangePayload(code)
                .orElseThrow(() -> new BusinessException("OAUTH_EXCHANGE_INVALID", "OAuth exchange code is invalid or expired"));
        try {
            return objectMapper.readValue(payload, AuthResponse.class);
        } catch (Exception e) {
            throw new BusinessException("OAUTH_EXCHANGE_INVALID", "OAuth exchange payload is invalid");
        }
    }

    public OAuthPendingProfileResponse getPendingRegistration(String code) {
        String payload = oauthStateService.peekPendingProfile(code)
                .orElseThrow(() -> new BusinessException("OAUTH_PENDING_INVALID", "Registration session expired or invalid"));
        try {
            OAuthProfilePayload profile = objectMapper.readValue(payload, OAuthProfilePayload.class);
            return new OAuthPendingProfileResponse(
                    profile.email(),
                    profile.fullName(),
                    profile.avatarUrl(),
                    profile.provider()
            );
        } catch (Exception e) {
            throw new BusinessException("OAUTH_PENDING_INVALID", "Registration session is invalid");
        }
    }

    @Transactional
    public AuthResponse completeRegistration(String code) {
        String payload = oauthStateService.consumePendingProfile(code)
                .orElseThrow(() -> new BusinessException("OAUTH_PENDING_INVALID", "Registration session expired or invalid"));
        try {
            OAuthProfilePayload profile = objectMapper.readValue(payload, OAuthProfilePayload.class);
            if (userRepository.findByAuthProviderAndProviderUserId(profile.provider(), profile.providerUserId()).isPresent()
                    || userRepository.findByEmailAndDeletedAtIsNull(profile.email()).isPresent()) {
                throw new BusinessException("OAUTH_ACCOUNT_EXISTS", "An account already exists for this email");
            }
            User user = createOAuthUser(profile);
            return issueAuthResponse(user);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("OAUTH_PENDING_INVALID", "Registration session is invalid");
        }
    }

    public String buildErrorRedirect(String message) {
        return frontendUrl + "/oauth/callback?error=" + urlEncode(message);
    }

    @Transactional
    protected String completeOAuthLogin(OAuthProfilePayload profile, String stateValue) {
        Optional<User> existingUser = userRepository.findByAuthProviderAndProviderUserId(profile.provider(), profile.providerUserId())
                .or(() -> userRepository.findByEmailAndDeletedAtIsNull(profile.email()));

        if (existingUser.isPresent()) {
            User user = finalizeExistingUser(existingUser.get(), profile);
            return buildAuthRedirect(user);
        }

        try {
            String pendingCode = oauthStateService.storePendingProfile(objectMapper.writeValueAsString(profile));
            return frontendUrl + "/oauth/complete?code=" + urlEncode(pendingCode);
        } catch (Exception e) {
            throw new BusinessException("OAUTH_PENDING_ERROR", "Unable to start registration");
        }
    }

    private User finalizeExistingUser(User user, OAuthProfilePayload profile) {
        if (!profile.provider().equals(user.getAuthProvider())) {
            if ("LOCAL".equals(user.getAuthProvider())) {
                user.setAuthProvider(profile.provider());
                user.setProviderUserId(profile.providerUserId());
            } else {
                throw new BusinessException("OAUTH_ACCOUNT_CONFLICT", "This email is linked to another sign-in method");
            }
        }

        if (user.getProviderUserId() == null) {
            user.setProviderUserId(profile.providerUserId());
        }
        if (profile.fullName() != null && (user.getFullName() == null || user.getFullName().isBlank())) {
            user.setFullName(profile.fullName());
        }
        if (profile.avatarUrl() != null) {
            user.setAvatarUrl(profile.avatarUrl());
        }
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(LocalDateTime.now());
        user.setLastLoginAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    private String buildAuthRedirect(User user) {
        revokeAllUserTokens(user);
        AuthResponse authResponse = issueAuthResponse(user);

        try {
            String exchangeCode = oauthStateService.storeExchangePayload(objectMapper.writeValueAsString(authResponse));
            return frontendUrl + "/oauth/callback?code=" + urlEncode(exchangeCode);
        } catch (Exception e) {
            throw new BusinessException("OAUTH_EXCHANGE_ERROR", "Unable to finalize OAuth login");
        }
    }

    private AuthResponse issueAuthResponse(User user) {
        String accessToken = jwtUtils.generateAccessToken(user);
        RefreshToken refreshToken = createRefreshToken(user);
        return authService.buildAuthResponsePublic(accessToken, refreshToken.getToken(), user);
    }

    private User createOAuthUser(OAuthProfilePayload profile) {
        String roleName = "ROLE_CREATOR";
        Role roleEntity = roleRepository.findByName(roleName)
                .orElseThrow(() -> new BusinessException("ROLE_NOT_FOUND", "Role not found: " + roleName));

        User user = new User();
        user.setEmail(profile.email());
        user.setFullName(profile.fullName() != null ? profile.fullName() : profile.email());
        user.setAvatarUrl(profile.avatarUrl());
        user.setAuthProvider(profile.provider());
        user.setProviderUserId(profile.providerUserId());
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(LocalDateTime.now());
        user.setRoles(Set.of(roleEntity));

        user = userRepository.save(user);
        log.info("OAuth user created: {} via {}", user.getEmail(), profile.provider());

        CreatorProfile creatorProfile = new CreatorProfile();
        creatorProfile.setUser(user);
        entityManager.persist(creatorProfile);

        return user;
    }

    private void revokeAllUserTokens(User user) {
        refreshTokenRepository.findAllByUserAndIsRevokedFalse(user).forEach(token -> {
            token.setIsRevoked(true);
            token.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(token);
        });
    }

    private RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(jwtUtils.generateRefreshTokenValue());
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(LocalDateTime.now().plusSeconds(refreshExpirationMs / 1000));
        refreshToken.setIsRevoked(false);
        return refreshTokenRepository.save(refreshToken);
    }

    private Map<String, Object> postForm(String url, MultiValueMap<String, String> form) {
        return webClientBuilder.build()
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(Map.class)
                .blockOptional()
                .orElseThrow(() -> new BusinessException("OAUTH_PROVIDER_ERROR", "OAuth provider request failed"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getJson(String url, String accessToken) {
        return webClientBuilder.build()
                .get()
                .uri(url)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(Map.class)
                .blockOptional()
                .orElseThrow(() -> new BusinessException("OAUTH_PROVIDER_ERROR", "OAuth provider request failed"));
    }

    private void ensureGoogleConfigured() {
        if (!oauthProperties.isGoogleConfigured()) {
            throw new BusinessException("OAUTH_NOT_CONFIGURED", "Google sign-in is not configured");
        }
    }

    private String required(Object value, String message) {
        String text = asString(value);
        if (text == null || text.isBlank()) {
            throw new BusinessException("OAUTH_PROFILE_INCOMPLETE", message);
        }
        return text;
    }

    private String optionalString(Object value) {
        return asString(value);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
