package com.plateforme.auth.service;

import com.plateforme.auth.dto.AuthResponse;
import com.plateforme.auth.dto.LoginRequest;
import com.plateforme.auth.dto.SignupRequest;
import com.plateforme.auth.security.JwtUtils;
import com.plateforme.ecosystem.storage.PublicMediaUrlResolver;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.dto.UserDto;
import com.plateforme.user.entity.CreatorProfile;
import com.plateforme.user.entity.RefreshToken;
import com.plateforme.user.entity.Role;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.RefreshTokenRepository;
import com.plateforme.user.repository.RoleRepository;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final RedisTemplate<String, String> redisTemplate;
    private final EntityManager entityManager;
    private final PublicMediaUrlResolver publicMediaUrlResolver;

    @Value("${app.jwt.refresh-expiration}")
    private long refreshExpirationMs;

    @Value("${app.jwt.expiration}")
    private long jwtExpirationMs;

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("EMAIL_ALREADY_EXISTS", "Un compte avec cet email existe déjà");
        }

        String roleName = "ROLE_CREATOR";
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new BusinessException("ROLE_NOT_FOUND", "Rôle introuvable: " + roleName));

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setRoles(Set.of(role));

        user = userRepository.save(user);
        log.info("Nouvel utilisateur créé: {} avec rôle {}", user.getEmail(), roleName);

        CreatorProfile profile = new CreatorProfile();
        profile.setUser(user);
        entityManager.persist(profile);
        log.info("Profil créateur créé pour l'utilisateur: {}", user.getEmail());

        String accessToken = jwtUtils.generateAccessToken(user);
        RefreshToken refreshToken = createRefreshToken(user);

        return buildAuthResponse(accessToken, refreshToken.getToken(), user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = (User) authentication.getPrincipal();
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        revokeAllUserTokens(user);

        String accessToken = jwtUtils.generateAccessToken(user);
        RefreshToken refreshToken = createRefreshToken(user);

        log.info("Connexion réussie pour: {}", user.getEmail());
        return buildAuthResponse(accessToken, refreshToken.getToken(), user);
    }

    @Transactional
    public AuthResponse refreshToken(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new BusinessException("INVALID_REFRESH_TOKEN", "Refresh token invalide"));

        if (Boolean.TRUE.equals(refreshToken.getIsRevoked())) {
            throw new BusinessException("REVOKED_REFRESH_TOKEN", "Refresh token révoqué");
        }

        if (refreshToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new BusinessException("EXPIRED_REFRESH_TOKEN", "Refresh token expiré");
        }

        User user = refreshToken.getUser();
        // Rotate refresh token at each refresh call to reduce replay window.
        refreshToken.setIsRevoked(true);
        refreshToken.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(refreshToken);

        RefreshToken newRefreshToken = createRefreshToken(user);
        String newAccessToken = jwtUtils.generateAccessToken(user);

        log.info("Access token renouvelé pour: {}", user.getEmail());
        return buildAuthResponse(newAccessToken, newRefreshToken.getToken(), user);
    }

    @Transactional
    public void logout(String refreshTokenValue, String accessTokenJti, long accessTokenRemainingMs) {
        refreshTokenRepository.findByToken(refreshTokenValue).ifPresent(token -> {
            token.setIsRevoked(true);
            token.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(token);
            log.info("Refresh token révoqué pour l'utilisateur: {}", token.getUser().getEmail());
        });

        if (accessTokenJti != null && accessTokenRemainingMs > 0) {
            redisTemplate.opsForValue().set(
                    "blacklist:" + accessTokenJti,
                    "revoked",
                    accessTokenRemainingMs,
                    TimeUnit.MILLISECONDS
            );
            log.debug("Access token mis en blacklist Redis, jti: {}", accessTokenJti);
        }
    }

    public boolean isTokenBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + jti));
    }

    private RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(jwtUtils.generateRefreshTokenValue());
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(LocalDateTime.now().plusSeconds(refreshExpirationMs / 1000));
        refreshToken.setIsRevoked(false);
        return refreshTokenRepository.save(refreshToken);
    }

    private void revokeAllUserTokens(User user) {
        refreshTokenRepository.findAllByUserAndIsRevokedFalse(user).forEach(token -> {
            token.setIsRevoked(true);
            token.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(token);
        });
    }

    private AuthResponse buildAuthResponse(String accessToken, String refreshToken, User user) {
        UserDto userDto = toUserDto(user);
        return new AuthResponse(accessToken, refreshToken, jwtExpirationMs / 1000, userDto);
    }

    public AuthResponse buildAuthResponsePublic(String accessToken, String refreshToken, User user) {
        return buildAuthResponse(accessToken, refreshToken, user);
    }

    public UserDto toUserDto(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                publicMediaUrlResolver.resolveAvatarUrl(user.getAvatarUrl()),
                roleNames,
                user.getCreatedAt(),
                user.getEmailVerified()
        );
    }
}
