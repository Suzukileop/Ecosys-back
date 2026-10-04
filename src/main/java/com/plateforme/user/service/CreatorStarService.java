package com.plateforme.user.service;

import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.dto.CreatorStarStatsDto;
import com.plateforme.user.entity.CreatorStar;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.CreatorStarRepository;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Trust stars: any signed-in user can give one star to another account, and take it back. */
@Service
@RequiredArgsConstructor
public class CreatorStarService {

    private final CreatorStarRepository creatorStarRepository;
    private final UserRepository userRepository;

    @Transactional
    public void star(UUID userId, UUID creatorId) {
        if (userId.equals(creatorId)) {
            throw new BusinessException("STAR_NOT_ALLOWED", "You cannot star your own account.");
        }
        if (creatorStarRepository.existsByCreator_IdAndUser_Id(creatorId, userId)) {
            return;
        }
        User creator = requireUser(creatorId, "CREATOR_NOT_FOUND", "Account not found.");
        User user = requireUser(userId, "USER_NOT_FOUND", "User not found.");

        CreatorStar star = new CreatorStar();
        star.setCreator(creator);
        star.setUser(user);
        creatorStarRepository.save(star);
    }

    @Transactional
    public void unstar(UUID userId, UUID creatorId) {
        creatorStarRepository.findByCreator_IdAndUser_Id(creatorId, userId)
                .ifPresent(creatorStarRepository::delete);
    }

    @Transactional(readOnly = true)
    public CreatorStarStatsDto getStats(UUID creatorId, UUID viewerUserId) {
        return new CreatorStarStatsDto(getStarCount(creatorId), isStarred(viewerUserId, creatorId));
    }

    @Transactional(readOnly = true)
    public long getStarCount(UUID creatorId) {
        return creatorStarRepository.countByCreator_Id(creatorId);
    }

    @Transactional(readOnly = true)
    public boolean isStarred(UUID viewerUserId, UUID creatorId) {
        return viewerUserId != null && creatorStarRepository.existsByCreator_IdAndUser_Id(creatorId, viewerUserId);
    }

    private User requireUser(UUID id, String code, String message) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(code, message));
    }
}
