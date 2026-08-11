package com.plateforme.auth.service;

import com.plateforme.auth.dto.AuthResponse;
import com.plateforme.auth.dto.LoginRequest;
import com.plateforme.auth.dto.SignupRequest;
import com.plateforme.auth.security.JwtUtils;
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
        testUser.setRoles(new HashSet<>(Set.of(testRole)));
        testUser.setEnabled(true);
        testUser.setAccountNonLocked(true);

        testRefreshToken = new RefreshToken();
        testRefreshToken.setId(UUID.randomUUID());
        testRefreshToken.setToken("refresh-token-value");
        testRefreshToken.setUser(testUser);
        testRefreshToken.setExpiryDate(LocalDateTime.now().plusDays(7));
        testRefreshToken.setIsRevoked(false);
    }

    // --- Tests signup ---

    @Test
    void signup_success_creator() {
        SignupRequest request = new SignupRequest("new@example.com", "password123", "New User", "CREATOR");

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(roleRepository.findByName("ROLE_CREATOR")).thenReturn(Optional.of(testRole));
        when(passwordEncoder.encode("password123")).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtUtils.generateAccessToken(any())).thenReturn("access-token");
        when(jwtUtils.generateRefreshTokenValue()).thenReturn("refresh-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(testRefreshToken);

        AuthResponse response = authService.signup(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        verify(userRepository).save(any(User.class));
        verify(entityManager).persist(any());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void signup_throwsBusinessException_whenEmailAlreadyExists() {
        SignupRequest request = new SignupRequest("test@example.com", "password123", "Test", "CREATOR");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("email")
                .extracting("code")
                .isEqualTo("EMAIL_ALREADY_EXISTS");
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
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(testRefreshToken);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        authService.logout("refresh-token-value", "some-jti", 60000L);

        verify(refreshTokenRepository).save(argThat(token ->
                Boolean.TRUE.equals(token.getIsRevoked()) && token.getRevokedAt() != null
        ));
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
