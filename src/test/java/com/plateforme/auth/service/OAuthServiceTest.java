package com.plateforme.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plateforme.auth.dto.OAuthProfilePayload;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OAuthServiceTest {

    @Mock
    private OAuthStateService oauthStateService;
    @Mock
    private AuthService authService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private EntityManager entityManager;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OAuthService oauthService;

    private final OAuthProfilePayload google =
            new OAuthProfilePayload("GOOGLE", "google-123", "owner@example.com", "Real Owner", null);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(oauthService, "frontendUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(oauthService, "refreshExpirationMs", 604800000L);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenRepository.findAllByUserAndIsRevokedFalse(any())).thenReturn(List.of());
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtUtils.generateAccessToken(any())).thenReturn("access");
        when(jwtUtils.generateRefreshTokenValue()).thenReturn("refresh");
        when(oauthStateService.storeExchangePayload(anyString())).thenReturn("exchange-code");
    }

    private User localAccount(boolean emailVerified) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("owner@example.com");
        user.setAuthProvider("LOCAL");
        user.setPasswordHash("hash-chosen-at-sign-up");
        user.setEmailVerified(emailVerified);
        return user;
    }

    @Test
    void googleSignIn_onUnconfirmedLocalAccount_dropsThePasswordSomeoneElseMayHaveChosen() {
        User squatted = localAccount(false);
        when(userRepository.findByAuthProviderAndProviderUserId("GOOGLE", "google-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndDeletedAtIsNull("owner@example.com")).thenReturn(Optional.of(squatted));

        oauthService.completeOAuthLogin(google, "state");

        assertThat(squatted.getPasswordHash()).isNull();
        assertThat(squatted.getAuthProvider()).isEqualTo("GOOGLE");
        assertThat(squatted.getEmailVerified()).isTrue();
    }

    @Test
    void googleSignIn_onConfirmedLocalAccount_keepsItsPassword() {
        User owner = localAccount(true);
        when(userRepository.findByAuthProviderAndProviderUserId("GOOGLE", "google-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndDeletedAtIsNull("owner@example.com")).thenReturn(Optional.of(owner));

        oauthService.completeOAuthLogin(google, "state");

        assertThat(owner.getPasswordHash()).isEqualTo("hash-chosen-at-sign-up");
        assertThat(owner.getAuthProvider()).isEqualTo("GOOGLE");
    }

    @Test
    void completeRegistration_createsTheAccountWithoutAPassword() throws Exception {
        String pendingProfile = objectMapper.writeValueAsString(google);
        when(oauthStateService.consumePendingProfile("pending")).thenReturn(Optional.of(pendingProfile));
        when(userRepository.findByAuthProviderAndProviderUserId("GOOGLE", "google-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndDeletedAtIsNull("owner@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(roleRepository.findByName("ROLE_CREATOR")).thenReturn(Optional.of(new Role("ROLE_CREATOR")));

        oauthService.completeRegistration("pending");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isNull();
        assertThat(saved.getValue().getEmailVerified()).isTrue();
        assertThat(saved.getValue().getRoles()).extracting(Role::getName).containsExactly("ROLE_CREATOR");
    }

    @Test
    void googleSignIn_onAccountLinkedToAnotherProvider_isRefused() {
        User other = localAccount(true);
        other.setAuthProvider("GITHUB");
        when(userRepository.findByAuthProviderAndProviderUserId("GOOGLE", "google-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndDeletedAtIsNull("owner@example.com")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> oauthService.completeOAuthLogin(google, "state"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("OAUTH_ACCOUNT_CONFLICT");
        assertThat(other.getPasswordHash()).isEqualTo("hash-chosen-at-sign-up");
    }
}
