package com.plateforme.user.service;

import com.plateforme.user.entity.CreatorProfile;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.CreatorProfileRepository;
import com.plateforme.user.repository.UserRepository;
import com.plateforme.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreatorProfileReadinessService {

    private final UserRepository userRepository;
    private final CreatorProfileRepository creatorProfileRepository;

    @Transactional(readOnly = true)
    public void requireReadyForProducts(UUID userId) {
        requireReady(userId, false);
    }

    @Transactional(readOnly = true)
    public void requireReadyForServices(UUID userId) {
        requireReady(userId, true);
    }

    private void requireReady(UUID userId, boolean requireSpecialties) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found: " + userId));
        CreatorProfile profile = creatorProfileRepository.findByUserId(userId).orElse(null);
        CreatorProfileReadinessSupport.requireReady(user, profile, requireSpecialties);
    }
}
