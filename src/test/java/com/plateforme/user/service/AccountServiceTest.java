package com.plateforme.user.service;

import com.plateforme.auth.service.EmailVerificationService;
import com.plateforme.auth.service.VerificationCodePurpose;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.dto.ChangePasswordRequest;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Settings › Security: changing a password, and the first password of an account made with Google. */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private AccountService accountService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("owner@example.com");
        when(userRepository.findByIdAndDeletedAtIsNull(user.getId())).thenReturn(Optional.of(user));
    }

    @Test
    void sendPasswordSetupCode_emailsAPasswordSetupCode_whenAccountHasNoPassword() {
        user.setPasswordHash(null);
        when(emailVerificationService.sendCode(user, "fr", VerificationCodePurpose.PASSWORD_SETUP)).thenReturn(60L);

        assertThat(accountService.sendPasswordSetupCode(user.getId(), "fr")).isEqualTo(60L);
    }

    @Test
    void sendPasswordSetupCode_isRefused_whenAccountAlreadyHasAPassword() {
        user.setPasswordHash("hash");

        assertThatThrownBy(() -> accountService.sendPasswordSetupCode(user.getId(), "fr"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("PASSWORD_ALREADY_SET");
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void firstPassword_withoutCode_isRefused() {
        user.setPasswordHash(null);

        assertThatThrownBy(() -> accountService.changePassword(
                user.getId(), new ChangePasswordRequest(null, null, "new-password-1")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("VERIFICATION_CODE_REQUIRED");
        verify(userRepository, never()).save(any());
    }

    @Test
    void firstPassword_withWrongCode_isRefused() {
        user.setPasswordHash(null);
        doThrow(new BusinessException("INVALID_VERIFICATION_CODE", "This code is incorrect."))
                .when(emailVerificationService).verify(user, "000000", VerificationCodePurpose.PASSWORD_SETUP);

        assertThatThrownBy(() -> accountService.changePassword(
                user.getId(), new ChangePasswordRequest(null, "000000", "new-password-1")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_VERIFICATION_CODE");
        assertThat(user.getPasswordHash()).isNull();
    }

    @Test
    void firstPassword_withValidCode_isSaved() {
        user.setPasswordHash(null);
        when(passwordEncoder.encode("new-password-1")).thenReturn("new-hash");

        accountService.changePassword(user.getId(), new ChangePasswordRequest(null, "123456", "new-password-1"));

        verify(emailVerificationService).verify(user, "123456", VerificationCodePurpose.PASSWORD_SETUP);
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(userRepository).save(user);
    }

    @Test
    void changingAnExistingPassword_stillNeedsTheCurrentOne_andNoCode() {
        user.setPasswordHash("old-hash");
        when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> accountService.changePassword(
                user.getId(), new ChangePasswordRequest("wrong", "123456", "new-password-1")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_CURRENT_PASSWORD");
        verifyNoInteractions(emailVerificationService);
    }
}
