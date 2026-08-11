package com.plateforme.credits.service;

import com.plateforme.credits.entity.UserCredit;
import com.plateforme.credits.exception.InsufficientCreditsException;
import com.plateforme.credits.repository.CreditTransactionRepository;
import com.plateforme.credits.repository.UserCreditRepository;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreditServiceTest {

    @Mock
    private UserCreditRepository userCreditRepository;

    @Mock
    private CreditTransactionRepository creditTransactionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CreditService creditService;

    private UUID userId;
    private UserCredit account;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        account = new UserCredit();
        account.setUser(user);
        account.setBalance(100);
    }

    @Test
    @DisplayName("getBalance retourne le solde existant")
    void getBalance_existing() {
        when(userCreditRepository.findByUser_Id(userId)).thenReturn(Optional.of(account));
        assertThat(creditService.getBalance(userId)).isEqualTo(100);
    }

    @Test
    @DisplayName("deduct réduit le solde")
    void deduct_success() {
        when(userCreditRepository.findByUser_Id(userId)).thenReturn(Optional.of(account));
        when(userRepository.getReferenceById(userId)).thenReturn(account.getUser());

        creditService.deduct(userId, 10, "Test", UUID.randomUUID());

        assertThat(account.getBalance()).isEqualTo(90);
        verify(userCreditRepository).save(account);
    }

    @Test
    @DisplayName("deduct solde insuffisant")
    void deduct_insufficient() {
        account.setBalance(5);
        when(userCreditRepository.findByUser_Id(userId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> creditService.deduct(userId, 10, "Test", null))
                .isInstanceOf(InsufficientCreditsException.class);
    }

    @Test
    @DisplayName("refund augmente le solde")
    void refund_success() {
        when(userCreditRepository.findByUser_Id(userId)).thenReturn(Optional.of(account));
        when(userRepository.getReferenceById(userId)).thenReturn(account.getUser());

        creditService.refund(userId, 15, "Remboursement", null);

        assertThat(account.getBalance()).isEqualTo(115);
    }

    @Test
    @DisplayName("setBalance fixe le solde absolu")
    void setBalance_success() {
        when(userCreditRepository.findByUser_Id(userId)).thenReturn(Optional.of(account));
        when(userRepository.getReferenceById(userId)).thenReturn(account.getUser());

        creditService.setBalance(userId, 500, "Ajustement admin");

        assertThat(account.getBalance()).isEqualTo(500);
        verify(userCreditRepository).save(account);
    }
}
