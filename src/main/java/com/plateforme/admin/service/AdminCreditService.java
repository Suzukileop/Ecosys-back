package com.plateforme.admin.service;

import com.plateforme.credits.dto.CreditBalanceResponse;
import com.plateforme.credits.service.CreditService;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminCreditService {

    private final UserRepository userRepository;
    private final CreditService creditService;

    @Transactional(readOnly = true)
    public CreditBalanceResponse getBalance(UUID userId) {
        User user = findUser(userId);
        return new CreditBalanceResponse(user.getId(), user.getEmail(), creditService.getBalance(userId));
    }

    @Transactional
    public CreditBalanceResponse setBalance(UUID userId, int balance) {
        User user = findUser(userId);
        creditService.setBalance(userId, balance, "Ajustement admin");
        return new CreditBalanceResponse(user.getId(), user.getEmail(), balance);
    }

    private User findUser(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND",
                        "Utilisateur introuvable : " + userId));
    }
}
