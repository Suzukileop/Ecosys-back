package com.plateforme.auth.service;

import com.plateforme.auth.dto.AuthResponse;
import com.plateforme.auth.dto.LoginRequest;
import com.plateforme.auth.dto.ResendVerificationRequest;
import com.plateforme.auth.dto.ResendVerificationResponse;
import com.plateforme.auth.dto.SignupRequest;
import com.plateforme.auth.dto.SignupResponse;
import com.plateforme.auth.dto.VerifyEmailRequest;
import com.plateforme.auth.security.JwtUtils;
import com.plateforme.shared.storage.PublicMediaUrlResolver;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.dto.UserDto;
import com.plateforme.user.entity.CreatorProfile;
import com.plateforme.user.entity.RefreshToken;
import com.plateforme.user.entity.Role;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.RefreshTokenRepository;
import com.plateforme.user.repository.RoleRepository;
import com.plateforme.user.repository.UserRepository;
import com.plateforme.user.support.UsernameSupport;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final EmailVerificationService emailVerificationService;

    @Value("${app.jwt.refresh-expiration}")
    private long refreshExpirationMs;

    @Value("${app.jwt.expiration}")
    private long jwtExpirationMs;

    /**
     * Creates a local account that stays locked until its e-mail is confirmed: no session is opened
     * here, a 6-digit code is e-mailed instead (see {@link #verifyEmail}).
     *
     * <p>An address registered but never confirmed doesn't block its real owner: signing up again
     * with it replaces the unconfirmed details (nobody has proven they own that inbox yet) and
     * sends a new code.
     */
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String username = UsernameSupport.normalize(request.username());
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.email())
                .map(existing -> {
                    if (Boolean.TRUE.equals(existing.getEmailVerified()) || !"LOCAL".equals(existing.getAuthProvider())) {
                        throw new BusinessException("EMAIL_ALREADY_EXISTS", "An account with this email already exists.");
                    }
                    return existing;
                })
                .orElse(null);

        if (user == null && userRepository.existsByEmail(request.email())) {
            throw new BusinessException("EMAIL_ALREADY_EXISTS", "An account with this email already exists.");
        }
        UsernameSupport.requireAvailable(userRepository, username, user == null ? null : user.getId());

        boolean isNew = user == null;
        if (isNew) {
            Role role = roleRepository.findByName("ROLE_CREATOR")
                    .orElseThrow(() -> new BusinessException("ROLE_NOT_FOUND", "Role not found."));
            user = new User();
            user.setEmail(request.email());
            user.setRoles(Set.of(role));
        }
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setPublicUsername(username);
        user.setEmailVerified(false);
        user.setEmailVerifiedAt(null);
        user = userRepository.save(user);

        if (isNew) {
            CreatorProfile profile = new CreatorProfile();
            profile.setUser(user);
            entityManager.persist(profile);
            log.info("Nouvel utilisateur créé (non vérifié): {}", user.getId());
        } else {
            log.info("Inscription relancée pour un compte non vérifié: {}", user.getId());
        }

        sendVerificationAfterCommit(user, request.locale());
        return new SignupResponse(
                user.getEmail(),
                true,
                EmailVerificationService.RESEND_COOLDOWN_SECONDS,
                EmailVerificationService.CODE_TTL_MINUTES);
    }

    /**
     * Confirms the e-mail with the code it received, then signs the user in — the first session
     * of a local account starts here.
     */
    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        /* An unknown address gets the same answer as a wrong code: the endpoint reveals nothing. */
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.email())
                .orElseThrow(() -> new BusinessException(
                        "INVALID_VERIFICATION_CODE", "This code is incorrect. Check it and try again."));
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new BusinessException("EMAIL_ALREADY_VERIFIED", "This email is already verified. You can log in.");
        }

        emailVerificationService.verify(user, request.code());

        LocalDateTime now = LocalDateTime.now();
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(now);
        user.setLastLoginAt(now);
        userRepository.save(user);
        log.info("Adresse e-mail vérifiée pour: {}", user.getId());

        String accessToken = jwtUtils.generateAccessToken(user);
        RefreshToken refreshToken = createRefreshToken(user);
        return buildAuthResponse(accessToken, refreshToken.getToken(), user);
    }

    /** Sends a new code if the address has an unconfirmed account; the answer is the same either way. */
    public ResendVerificationResponse resendVerification(ResendVerificationRequest request) {
        return userRepository.findByEmailAndDeletedAtIsNull(request.email())
                .filter(user -> !Boolean.TRUE.equals(user.getEmailVerified()))
                .map(user -> new ResendVerificationResponse(emailVerificationService.sendCode(user, request.locale())))
                .orElseGet(() -> new ResendVerificationResponse(EmailVerificationService.RESEND_COOLDOWN_SECONDS));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = (User) authentication.getPrincipal();

        /* Checked after the password, so this answer never tells a stranger the account exists. */
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            emailVerificationService.sendCodeQuietly(user, request.locale());
            throw new BusinessException("EMAIL_NOT_VERIFIED", "Please confirm your email address to continue.");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        revokeAllUserTokens(user);

        String accessToken = jwtUtils.generateAccessToken(user);
        RefreshToken refreshToken = createRefreshToken(user);

        log.info("Connexion réussie pour: {}", user.getId());
        return buildAuthResponse(accessToken, refreshToken.getToken(), user);
    }

    @Transactional
    public AuthResponse refreshToken(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new BusinessException("INVALID_REFRESH_TOKEN", "Your session is invalid. Please sign in again."));

        if (Boolean.TRUE.equals(refreshToken.getIsRevoked())) {
            throw new BusinessException("REVOKED_REFRESH_TOKEN", "Your session has ended. Please sign in again.");
        }

        if (refreshToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new BusinessException("EXPIRED_REFRESH_TOKEN", "Your session has expired. Please sign in again.");
        }

        User user = refreshToken.getUser();
        // Rotate refresh token at each refresh call to reduce replay window.
        refreshToken.setIsRevoked(true);
        refreshToken.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(refreshToken);

        RefreshToken newRefreshToken = createRefreshToken(user);
        String newAccessToken = jwtUtils.generateAccessToken(user);

        log.info("Access token renouvelé pour: {}", user.getId());
        return buildAuthResponse(newAccessToken, newRefreshToken.getToken(), user);
    }

    @Transactional
    public void logout(String refreshTokenValue, String accessTokenJti, long accessTokenRemainingMs) {
        refreshTokenRepository.findByToken(refreshTokenValue).ifPresent(token -> {
            refreshTokenRepository.delete(token);
            log.info("Refresh token supprimé à la déconnexion pour l'utilisateur: {}", token.getUser().getId());
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

    /** The code goes out once the account is committed, so a rolled-back sign-up never e-mails anyone. */
    private void sendVerificationAfterCommit(User user, String locale) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            emailVerificationService.sendCodeQuietly(user, locale);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                emailVerificationService.sendCodeQuietly(user, locale);
            }
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
                user.getPublicUsername(),
                publicMediaUrlResolver.resolveAvatarUrl(user.getAvatarUrl()),
                roleNames,
                user.getCreatedAt(),
                user.getEmailVerified()
        );
    }
}
