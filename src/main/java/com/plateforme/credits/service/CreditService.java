package com.plateforme.credits.service;

import com.plateforme.credits.entity.CreditTransaction;
import com.plateforme.credits.entity.UserCredit;
import com.plateforme.credits.exception.InsufficientCreditsException;
import com.plateforme.credits.repository.CreditTransactionRepository;
import com.plateforme.credits.repository.UserCreditRepository;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreditService {

    private final UserCreditRepository userCreditRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public int getBalance(UUID userId) {
        return userCreditRepository.findByUser_Id(userId)
                .map(UserCredit::getBalance)
                .orElse(0);
    }

    @Transactional
    public void deduct(UUID userId, int amount, String reason, UUID refId) {
        if (amount <= 0) {
            throw new BusinessException("INVALID_CREDIT_AMOUNT", "Le montant doit être positif");
        }
        UserCredit account = getOrCreateAccount(userId);
        if (account.getBalance() < amount) {
            throw new InsufficientCreditsException(amount, account.getBalance());
        }
        account.setBalance(account.getBalance() - amount);
        userCreditRepository.save(account);
        saveTransaction(userId, -amount, reason, refId);
        log.info("Crédits déduits user={} amount={} reason={}", userId, amount, reason);
    }

    @Transactional
    public void refund(UUID userId, int amount, String reason, UUID refId) {
        if (amount <= 0) {
            throw new BusinessException("INVALID_CREDIT_AMOUNT", "Le montant doit être positif");
        }
        UserCredit account = getOrCreateAccount(userId);
        account.setBalance(account.getBalance() + amount);
        userCreditRepository.save(account);
        saveTransaction(userId, amount, reason, refId);
        log.info("Crédits remboursés user={} amount={} reason={}", userId, amount, reason);
    }

    @Transactional
    public void addCredits(UUID userId, int amount, String reason, UUID refId) {
        if (amount <= 0) {
            throw new BusinessException("INVALID_CREDIT_AMOUNT", "Le montant doit être positif");
        }
        UserCredit account = getOrCreateAccount(userId);
        account.setBalance(account.getBalance() + amount);
        userCreditRepository.save(account);
        saveTransaction(userId, amount, reason, refId);
    }

    /**
     * Fixe le solde à une valeur absolue (ajustement admin / seed).
     */
    @Transactional
    public void setBalance(UUID userId, int balance, String reason) {
        if (balance < 0) {
            throw new BusinessException("INVALID_CREDIT_AMOUNT", "Le solde ne peut pas être négatif");
        }
        UserCredit account = getOrCreateAccount(userId);
        int previous = account.getBalance();
        if (previous == balance) {
            return;
        }
        int delta = balance - previous;
        account.setBalance(balance);
        userCreditRepository.save(account);
        saveTransaction(userId, delta, reason, null);
        log.info("Solde crédits ajusté user={} {} -> {} ({})", userId, previous, balance, reason);
    }

    private UserCredit getOrCreateAccount(UUID userId) {
        return userCreditRepository.findByUser_Id(userId)
                .orElseGet(() -> {
                    User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                            .orElseThrow(() -> new BusinessException("USER_NOT_FOUND",
                                    "Utilisateur introuvable : " + userId));
                    UserCredit uc = new UserCredit();
                    uc.setUser(user);
                    uc.setBalance(0);
                    return userCreditRepository.save(uc);
                });
    }

    private void saveTransaction(UUID userId, int amount, String reason, UUID refId) {
        User user = userRepository.getReferenceById(userId);
        CreditTransaction tx = new CreditTransaction();
        tx.setUser(user);
        tx.setAmount(amount);
        tx.setReason(reason);
        tx.setRefId(refId);
        creditTransactionRepository.save(tx);
    }
}
