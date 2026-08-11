package com.plateforme.admin.service;

import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.dto.CreatorProfileDto;
import com.plateforme.user.entity.CreatorProfile;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.CreatorProfileRepository;
import com.plateforme.user.repository.UserRepository;
import com.plateforme.user.service.CreatorProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreatorAdminService {

    private final CreatorProfileRepository creatorProfileRepository;
    private final UserRepository userRepository;
    private final CreatorProfileService creatorProfileService;

    @Transactional
    public CreatorProfileDto setVerified(UUID creatorUserId, boolean verified) {
        User user = userRepository.findByIdAndDeletedAtIsNull(creatorUserId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND",
                        "User not found: " + creatorUserId));

        boolean hasCreatorRole = user.getRoles().stream()
                .anyMatch(r -> "ROLE_CREATOR".equals(r.getName()));
        if (!hasCreatorRole) {
            throw new BusinessException("ROLE_REQUIRED", "User must have CREATOR role");
        }

        CreatorProfile profile = creatorProfileRepository.findByUserId(creatorUserId)
                .orElseThrow(() -> new BusinessException("CREATOR_PROFILE_NOT_FOUND",
                        "Creator profile not found for user: " + creatorUserId));

        profile.setIsVerified(verified);
        creatorProfileRepository.save(profile);
        log.info("Creator verified={} for user={}", verified, creatorUserId);

        return creatorProfileService.getMyProfile(creatorUserId);
    }
}
