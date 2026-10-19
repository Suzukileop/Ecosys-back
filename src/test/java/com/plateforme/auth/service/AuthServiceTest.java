package com.plateforme.auth.service;

import com.plateforme.auth.dto.AuthResponse;
import com.plateforme.auth.dto.ForgotPasswordRequest;
import com.plateforme.auth.dto.ResetPasswordRequest;
import com.plateforme.auth.dto.LoginRequest;
import com.plateforme.auth.dto.SignupRequest;
import com.plateforme.auth.dto.SignupResponse;
import com.plateforme.auth.dto.VerifyEmailRequest;
import com.plateforme.auth.security.JwtUtils;
import com.plateforme.shared.storage.PublicMediaUrlResolver;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.entity.RefreshToken;
import com.plateforme.user.entity.Role;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.RefreshTokenRepository;
import com.plateforme.user.repository.RoleRepository;
import com.plateforme.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private EntityManager entityManager;
    @Mock
    private PublicMediaUrlResolver publicMediaUrlResolver;
    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private Role testRole;
    private RefreshToken testRefreshToken;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshExpirationMs", 604800000L);
        ReflectionTestUtils.setField(authService, "jwtExpirationMs", 900000L);

        testRole = new Role("ROLE_CREATOR");
        testRole = setId(testRole, UUID.randomUUID());

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@example.com");
        testUser.setPasswordHash("hashedPassword");
        testUser.setFullName("Test User");
        testUser.setPublicUsername("testuser");
        testUser.setRoles(new HashSet<>(Set.of(testRole)));
        testUser.setEnabled(true);
        testUser.setAccountNonLocked(true);
        testUser.setAuthProvider("LOCAL");
        testUser.setEmailVerified(true);

        testRefreshToken = new RefreshToken();
        testRefreshToken.setId(UUID.randomUUID());
        testRefreshToken.setToken("refresh-token-value");
        testRefreshToken.setUser(testUser);
        testRefreshToken.setExpiryDate(LocalDateTime.now().plusDays(7));
        testRefreshToken.setIsRevoked(false);
    }

    // --- Tests signup ---

    @Test
    void signup_success_creator_opensNoSessionAndSendsCode() {
        SignupRequest request = new SignupRequest("new@example.com", "password123", "New User", "newuser", "CREATOR");

        when(userRepository.findByEmailAndDeletedAtIsNull("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(roleRepository.findByName("ROLE_CREATOR")).thenReturn(Optional.of(testRole));
        when(passwordEncoder.encode("password123")).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        SignupResponse response = authService.signup(request);

        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(response.verificationRequired()).isTrue();
        assertThat(response.codeTtlMinutes()).isEqualTo(EmailVerificationService.CODE_TTL_MINUTES);
        verify(entityManager).persist(any());
        verify(emailVerificationService).sendCodeQuietly(any(User.class), any());
        verifyNoInteractions(refreshTokenRepository, jwtUtils);
    }

    @Test
    void signup_reusesUnverifiedLocalAccount_andResendsCode() {
        User pending = new User();
        pending.setId(UUID.randomUUID());
        pending.setEmail("pending@example.com");
        pending.setAuthProvider("LOCAL");
        pending.setEmailVerified(false);
        SignupRequest request = new SignupRequest("pending@example.com", "password123", "Real Owner", "realowner", "CREATOR");

        when(userRepository.findByEmailAndDeletedAtIsNull("pending@example.com")).thenReturn(Optional.of(pending));
        when(userRepository.existsByUsernameAndIdNot("realowner", pending.getId())).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("newHash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.signup(request);

        assertThat(pending.getFullName()).isEqualTo("Real Owner");
        assertThat(pending.getPasswordHash()).isEqualTo("newHash");
        verify(entityManager, never()).persist(any());
        verify(emailVerificationService).sendCodeQuietly(eq(pending), any());
    }

    @Test
    void signup_throwsEmailAlreadyExists_whenAccountIsVerified() {
        SignupRequest request = new SignupRequest("test@example.com", "password123", "Test", "other", "CREATOR");

        when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("EMAIL_ALREADY_EXISTS");
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void signup_throwsBusinessException_whenEmailAlreadyExists() {
        SignupRequest request = new SignupRequest("test@example.com", "password123", "Test", "testuser", "CREATOR");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("email")
                .extracting("code")
                .isEqualTo("EMAIL_ALREADY_EXISTS");
    }

    @Test
    void signup_throwsBusinessException_whenUsernameAlreadyExists() {
        SignupRequest request = new SignupRequest("new@example.com", "password123", "New User", "Leopard", "CREATOR");

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("Leopard")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("USERNAME_ALREADY_EXISTS");
    }

    // --- Tests login ---

    @Test
    void login_success() {
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(authToken);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(refreshTokenRepository.findAllByUserAndIsRevokedFalse(testUser)).thenReturn(List.of());
        when(jwtUtils.generateAccessToken(any())).thenReturn("access-token");
        when(jwtUtils.generateRefreshTokenValue()).thenReturn("new-refresh-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(testRefreshToken);
        when(publicMediaUrlResolver.resolveAvatarUrl(any())).thenReturn(null);

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access-token");
        verify(authenticationManager).authenticate(any());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void login_throwsBadCredentialsException_whenInvalidCredentials() {
        LoginRequest request = new LoginRequest("test@example.com", "wrongpassword");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Identifiants incorrects"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_throwsEmailNotVerified_andResendsCode_whenEmailUnconfirmed() {
        testUser.setEmailVerified(false);
        LoginRequest request = new LoginRequest("test@example.com", "password123", "fr");
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(authToken);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("EMAIL_NOT_VERIFIED");
        verify(emailVerificationService).sendCodeQuietly(testUser, "fr");
        verifyNoInteractions(jwtUtils);
    }

    // --- Tests verifyEmail ---

    @Test
    void verifyEmail_marksVerified_andOpensSession() {
        testUser.setEmailVerified(false);
        when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtUtils.generateAccessToken(any())).thenReturn("access-token");
        when(jwtUtils.generateRefreshTokenValue()).thenReturn("refresh-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(testRefreshToken);
        when(publicMediaUrlResolver.resolveAvatarUrl(any())).thenReturn(null);

        AuthResponse response = authService.verifyEmail(new VerifyEmailRequest("test@example.com", "123456"));

        verify(emailVerificationService).verify(testUser, "123456");
        assertThat(testUser.getEmailVerified()).isTrue();
        assertThat(testUser.getEmailVerifiedAt()).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    void verifyEmail_rejectsWrongCode_withoutVerifying() {
        testUser.setEmailVerified(false);
        when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(Optional.of(testUser));
        doThrow(new BusinessException("INVALID_VERIFICATION_CODE", "This code is incorrect."))
                .when(emailVerificationService).verify(testUser, "000000");

        assertThatThrownBy(() -> authService.verifyEmail(new VerifyEmailRequest("test@example.com", "000000")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_VERIFICATION_CODE");
        assertThat(testUser.getEmailVerified()).isFalse();
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmail_rejectsAlreadyVerifiedAccount() {
        when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> authService.verifyEmail(new VerifyEmailRequest("test@example.com", "123456")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("EMAIL_ALREADY_VERIFIED");
        verifyNoInteractions(emailVerificationService);
    }

    // --- Tests forgot / reset password ---

    @Test
    void forgotPassword_emailsAResetCode_toAnExistingAccount() {
        when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(Optional.of(testUser));

        var response = authService.forgotPassword(new ForgotPasswordRequest("test@example.com", "fr"));

        verify(emailVerificationService).sendCodeQuietly(testUser, "fr", VerificationCodePurpose.PASSWORD_RESET);
        assertThat(response.resendCooldownSeconds()).isEqualTo(EmailVerificationService.RESEND_COOLDOWN_SECONDS);
    }

    @Test
    void forgotPassword_answersTheSame_forAnUnknownAddress() {
        when(userRepository.findByEmailAndDeletedAtIsNull("nobody@example.com")).thenReturn(Optional.empty());

        var response = authService.forgotPassword(new ForgotPasswordRequest("nobody@example.com", "fr"));

        verifyNoInteractions(emailVerificationService);
        assertThat(response.resendCooldownSeconds()).isEqualTo(EmailVerificationService.RESEND_COOLDOWN_SECONDS);
    }

    @Test
    void resetPassword_setsThePassword_confirmsTheEmail_andEndsEverySession() {
        testUser.setEmailVerified(false);
        testUser.setPasswordHash(null);
        when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("new-password-1")).thenReturn("new-hash");
        when(refreshTokenRepository.findAllByUserAndIsRevokedFalse(testUser)).thenReturn(List.of(testRefreshToken));

        authService.resetPassword(new ResetPasswordRequest("test@example.com", "123456", "new-password-1"));

        verify(emailVerificationService).verify(testUser, "123456", VerificationCodePurpose.PASSWORD_RESET);
        assertThat(testUser.getPasswordHash()).isEqualTo("new-hash");
        assertThat(testUser.getEmailVerified()).isTrue();
        assertThat(testRefreshToken.getIsRevoked()).isTrue();
        verifyNoInteractions(jwtUtils);
    }

    @Test
    void resetPassword_withWrongCode_changesNothing() {
        when(userRepository.findByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(Optional.of(testUser));
        doThrow(new BusinessException("INVALID_VERIFICATION_CODE", "This code is incorrect."))
                .when(emailVerificationService).verify(testUser, "000000", VerificationCodePurpose.PASSWORD_RESET);

        assertThatThrownBy(() -> authService.resetPassword(
                new ResetPasswordRequest("test@example.com", "000000", "new-password-1")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_VERIFICATION_CODE");
        assertThat(testUser.getPasswordHash()).isEqualTo("hashedPassword");
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_forAnUnknownAddress_looksLikeAWrongCode() {
        when(userRepository.findByEmailAndDeletedAtIsNull("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(
                new ResetPasswordRequest("nobody@example.com", "123456", "new-password-1")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_VERIFICATION_CODE");
        verifyNoInteractions(emailVerificationService);
    }

    // --- Tests refreshToken ---

    @Test
    void refreshToken_success() {
        when(refreshTokenRepository.findByToken("refresh-token-value")).thenReturn(Optional.of(testRefreshToken));
        when(jwtUtils.generateAccessToken(any())).thenReturn("new-access-token");
        when(jwtUtils.generateRefreshTokenValue()).thenReturn("rotated-refresh-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.refreshToken("refresh-token-value");

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("new-access-token");
    }

    @Test
    void refreshToken_throwsBusinessException_whenRevoked() {
        testRefreshToken.setIsRevoked(true);
        when(refreshTokenRepository.findByToken("refresh-token-value")).thenReturn(Optional.of(testRefreshToken));

        assertThatThrownBy(() -> authService.refreshToken("refresh-token-value"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("REVOKED_REFRESH_TOKEN");
    }

    @Test
    void refreshToken_throwsBusinessException_whenExpired() {
        testRefreshToken.setExpiryDate(LocalDateTime.now().minusDays(1));
        when(refreshTokenRepository.findByToken("refresh-token-value")).thenReturn(Optional.of(testRefreshToken));

        assertThatThrownBy(() -> authService.refreshToken("refresh-token-value"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("EXPIRED_REFRESH_TOKEN");
    }

    // --- Tests logout ---

    @Test
    void logout_success() {
        when(refreshTokenRepository.findByToken("refresh-token-value")).thenReturn(Optional.of(testRefreshToken));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        authService.logout("refresh-token-value", "some-jti", 60000L);

        verify(refreshTokenRepository).delete(testRefreshToken);
        verify(valueOperations).set(eq("blacklist:some-jti"), eq("revoked"), eq(60000L), any());
    }

    private Role setId(Role role, UUID id) {
        try {
            var field = Role.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(role, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return role;
    }
}
