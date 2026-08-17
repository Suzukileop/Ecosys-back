package com.plateforme.user.service;

import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.dto.CreatorFollowStatsDto;
import com.plateforme.user.dto.CreatorFollowItemDto;
import com.plateforme.user.entity.CreatorFollow;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.CreatorFollowRepository;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreatorFollowService {

    private final CreatorFollowRepository creatorFollowRepository;
    private final UserRepository userRepository;

    @Transactional
    public void follow(UUID followerId, UUID creatorId) {
        if (followerId.equals(creatorId)) {
            throw new BusinessException("FOLLOW_NOT_ALLOWED", "You cannot follow yourself.");
        }

        User creator = requireCreator(creatorId);
        User follower = userRepository.findByIdAndDeletedAtIsNull(followerId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found."));

        if (creatorFollowRepository.existsByFollower_IdAndCreator_Id(followerId, creatorId)) {
            return;
        }

        CreatorFollow follow = new CreatorFollow();
        follow.setFollower(follower);
        follow.setCreator(creator);
        creatorFollowRepository.save(follow);
    }

    @Transactional
    public void unfollow(UUID followerId, UUID creatorId) {
        creatorFollowRepository.findByFollower_IdAndCreator_Id(followerId, creatorId)
                .ifPresent(creatorFollowRepository::delete);
    }

    @Transactional(readOnly = true)
    public Page<CreatorFollowItemDto> listFollowersForCreator(UUID creatorId, Pageable pageable) {
        requireCreator(creatorId);
        // Unsorted pageable: ranking is defined in the native query (Sort would conflict).
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<CreatorFollow> page = creatorFollowRepository.findFollowersRankedByMessagingActivity(
                creatorId, unsorted);
        List<CreatorFollowItemDto> items = page.getContent().stream().map(this::toFollowItemDto).toList();
        return new PageImpl<>(items, unsorted, page.getTotalElements());
    }

    private CreatorFollowItemDto toFollowItemDto(CreatorFollow follow) {
        User follower = follow.getFollower();
        String followerFullName = follower.getFullName() != null && !follower.getFullName().isBlank()
                ? follower.getFullName().trim()
                : "User";
        return new CreatorFollowItemDto(
                follow.getId(),
                follow.getCreatedAt(),
                follower.getId(),
                followerFullName,
                follower.getAvatarUrl());
    }

    @Transactional(readOnly = true)
    public CreatorFollowStatsDto getStats(UUID creatorId, UUID viewerUserId) {
        requireCreator(creatorId);
        long followerCount = creatorFollowRepository.countByCreator_Id(creatorId);
        boolean isFollowing = viewerUserId != null
                && creatorFollowRepository.existsByFollower_IdAndCreator_Id(viewerUserId, creatorId);
        return new CreatorFollowStatsDto(followerCount, isFollowing);
    }

    @Transactional(readOnly = true)
    public long getFollowerCount(UUID creatorId) {
        return creatorFollowRepository.countByCreator_Id(creatorId);
    }

    @Transactional(readOnly = true)
    public boolean isFollowing(UUID followerId, UUID creatorId) {
        if (followerId == null) {
            return false;
        }
        return creatorFollowRepository.existsByFollower_IdAndCreator_Id(followerId, creatorId);
    }

    @Transactional(readOnly = true)
    public Set<UUID> getFollowedCreatorIds(UUID followerId, Collection<UUID> creatorIds) {
        if (followerId == null || creatorIds == null || creatorIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(creatorFollowRepository.findFollowedCreatorIds(followerId, creatorIds));
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> getFollowerCounts(Collection<UUID> creatorIds) {
        if (creatorIds == null || creatorIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : creatorFollowRepository.countFollowersGrouped(creatorIds)) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private User requireCreator(UUID creatorId) {
        User creator = userRepository.findByIdAndDeletedAtIsNull(creatorId)
                .orElseThrow(() -> new BusinessException("CREATOR_NOT_FOUND", "Creator not found."));
        boolean isCreator = creator.getRoles().stream().anyMatch(r -> "ROLE_CREATOR".equals(r.getName()));
        if (!isCreator) {
            throw new BusinessException("CREATOR_NOT_FOUND", "Creator not found.");
        }
        return creator;
    }
}
