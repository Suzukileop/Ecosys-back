package com.plateforme.marketplace.service;

import com.plateforme.marketplace.dto.CreatorProfileViewResponse;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.shared.service.NotificationService;
import com.plateforme.user.dto.CreatorProfileVisitItemDto;
import com.plateforme.user.entity.CreatorProfile;
import com.plateforme.user.entity.CreatorProfileVisit;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.CreatorProfileRepository;
import com.plateforme.user.repository.CreatorProfileVisitRepository;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreatorProfileViewService {

    private static final Duration VISIT_COOLDOWN = Duration.ofHours(24);

    private final CreatorProfileRepository creatorProfileRepository;
    private final CreatorProfileVisitRepository visitRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public CreatorProfileViewResponse recordView(UUID creatorUserId, UUID viewerUserId, String anonymousVisitorKey) {
        CreatorProfile profile = creatorProfileRepository.findByUserIdAndUserDeletedAtIsNull(creatorUserId)
                .orElseThrow(() -> new BusinessException("CREATOR_NOT_FOUND", "Creator not found."));

        if (viewerUserId != null && viewerUserId.equals(creatorUserId)) {
            return new CreatorProfileViewResponse(false, safeVisits(profile));
        }

        String visitorKey = resolveVisitorKey(viewerUserId, anonymousVisitorKey);
        LocalDateTime now = LocalDateTime.now();
        var existing = visitRepository.findByCreatorUserIdAndVisitorKey(creatorUserId, visitorKey);

        if (existing.isPresent()) {
            CreatorProfileVisit visit = existing.get();
            if (Duration.between(visit.getViewedAt(), now).compareTo(VISIT_COOLDOWN) < 0) {
                return new CreatorProfileViewResponse(false, safeVisits(profile));
            }
            visit.setViewedAt(now);
            if (viewerUserId != null) {
                visit.setViewerUserId(viewerUserId);
            }
            visitRepository.save(visit);
        } else {
            CreatorProfileVisit visit = new CreatorProfileVisit();
            visit.setCreatorUserId(creatorUserId);
            visit.setViewerUserId(viewerUserId);
            visit.setVisitorKey(visitorKey);
            visit.setViewedAt(now);
            visitRepository.save(visit);
        }

        int visits = safeVisits(profile) + 1;
        profile.setProfileVisits(visits);
        creatorProfileRepository.save(profile);

        notifyCreator(profile.getUser(), viewerUserId, creatorUserId);

        log.debug("Creator profile visit recorded creator={} visitorKey={}", creatorUserId, visitorKey);
        return new CreatorProfileViewResponse(true, visits);
    }

    @Transactional(readOnly = true)
    public Page<CreatorProfileVisitItemDto> listVisitsForCreator(UUID creatorUserId, Pageable pageable) {
        creatorProfileRepository.findByUserIdAndUserDeletedAtIsNull(creatorUserId)
                .orElseThrow(() -> new BusinessException("CREATOR_NOT_FOUND", "Creator not found."));

        Page<CreatorProfileVisit> page = visitRepository.findByCreatorUserIdOrderByViewedAtDesc(creatorUserId, pageable);
        List<UUID> viewerIds = page.getContent().stream()
                .map(CreatorProfileVisit::getViewerUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, User> viewersById = viewerIds.isEmpty()
                ? Map.of()
                : userRepository.findByIdInAndDeletedAtIsNull(viewerIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        return page.map(visit -> toVisitItemDto(visit, viewersById));
    }

    private static CreatorProfileVisitItemDto toVisitItemDto(CreatorProfileVisit visit, Map<UUID, User> viewersById) {
        UUID viewerUserId = visit.getViewerUserId();
        boolean anonymous = viewerUserId == null;
        User viewer = anonymous ? null : viewersById.get(viewerUserId);
        String viewerFullName = null;
        String viewerAvatarUrl = null;
        if (!anonymous) {
            if (viewer != null && viewer.getFullName() != null && !viewer.getFullName().isBlank()) {
                viewerFullName = viewer.getFullName().trim();
            } else {
                viewerFullName = "User";
            }
            if (viewer != null) {
                viewerAvatarUrl = viewer.getAvatarUrl();
            }
        }
        return new CreatorProfileVisitItemDto(
                visit.getId(),
                visit.getViewedAt(),
                anonymous,
                viewerUserId,
                viewerFullName,
                viewerAvatarUrl
        );
    }

    private void notifyCreator(User creator, UUID viewerUserId, UUID creatorUserId) {
        String message;
        if (viewerUserId != null) {
            User viewer = userRepository.findByIdAndDeletedAtIsNull(viewerUserId).orElse(null);
            String viewerName = viewer != null && viewer.getFullName() != null && !viewer.getFullName().isBlank()
                    ? viewer.getFullName().trim()
                    : "A user";
            message = viewerName + " visited your profile.";
        } else {
            message = "Someone visited your profile.";
        }

        notificationService.createAndSend(
                creatorUserId,
                "CREATOR_PROFILE_VISIT",
                "Profile visit",
                message,
                "PLATFORM",
                creatorUserId,
                viewerUserId
        );
    }

    private static String resolveVisitorKey(UUID viewerUserId, String anonymousVisitorKey) {
        if (viewerUserId != null) {
            return "user:" + viewerUserId;
        }
        if (anonymousVisitorKey == null || anonymousVisitorKey.isBlank()) {
            throw new BusinessException("VISITOR_KEY_REQUIRED", "Anonymous visitor key is required.");
        }
        String trimmed = anonymousVisitorKey.trim();
        if (trimmed.length() > 100) {
            throw new BusinessException("VISITOR_KEY_INVALID", "Visitor key is too long.");
        }
        return "anon:" + trimmed;
    }

    private static int safeVisits(CreatorProfile profile) {
        return profile.getProfileVisits() != null ? profile.getProfileVisits() : 0;
    }
}
