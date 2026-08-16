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

    /**
     * Unique visitor rows are upserted (one row per visitor_key).
     * Each visit increments {@code visit_count} and refreshes {@code viewed_at}.
     * The displayed VISITS total stays unique-visitor count — never raw page loads.
     */
    private final CreatorProfileRepository creatorProfileRepository;
    private final CreatorProfileVisitRepository visitRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public CreatorProfileViewResponse recordView(UUID creatorUserId, UUID viewerUserId, String anonymousVisitorKey) {
        CreatorProfile profile = creatorProfileRepository.findByUserIdAndUserDeletedAtIsNull(creatorUserId)
                .orElseThrow(() -> new BusinessException("CREATOR_NOT_FOUND", "Creator not found."));

        if (viewerUserId != null && viewerUserId.equals(creatorUserId)) {
            return new CreatorProfileViewResponse(false, uniqueVisitorCount(creatorUserId, profile));
        }

        String visitorKey = resolveVisitorKey(viewerUserId, anonymousVisitorKey);
        LocalDateTime now = LocalDateTime.now();
        var existing = visitRepository.findByCreatorUserIdAndVisitorKey(creatorUserId, visitorKey);

        if (existing.isPresent()) {
            CreatorProfileVisit visit = existing.get();
            visit.setViewedAt(now);
            if (viewerUserId != null) {
                visit.setViewerUserId(viewerUserId);
            }
            int previous = visit.getVisitCount() != null && visit.getVisitCount() > 0 ? visit.getVisitCount() : 1;
            visit.setVisitCount(previous + 1);
            visitRepository.save(visit);

            // Same person again: unique count unchanged, but notify on every visit (registered only).
            long count = uniqueVisitorCount(creatorUserId, profile);
            syncDenormalizedCount(profile, count);
            notifyCreatorOfVisit(viewerUserId, creatorUserId);
            return new CreatorProfileViewResponse(false, count);
        }

        CreatorProfileVisit visit = new CreatorProfileVisit();
        visit.setCreatorUserId(creatorUserId);
        visit.setViewerUserId(viewerUserId);
        visit.setVisitorKey(visitorKey);
        visit.setViewedAt(now);
        visit.setVisitCount(1);
        visitRepository.save(visit);

        long count = visitRepository.countByCreatorUserId(creatorUserId);
        syncDenormalizedCount(profile, count);

        notifyCreatorOfVisit(viewerUserId, creatorUserId);

        log.debug("Creator profile unique visit recorded creator={} visitorKey={} count={}",
                creatorUserId, visitorKey, count);
        return new CreatorProfileViewResponse(true, count);
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

        Map<UUID, CreatorProfile> viewerProfilesByUserId = viewerIds.isEmpty()
                ? Map.of()
                : creatorProfileRepository.findByUser_IdIn(viewerIds).stream()
                        .filter(cp -> cp.getUser() != null)
                        .collect(Collectors.toMap(cp -> cp.getUser().getId(), Function.identity(), (a, b) -> a));

        return page.map(visit -> toVisitItemDto(visit, viewersById, viewerProfilesByUserId));
    }

    /** Authoritative unique-visitor total (same source as Visitors tab {@code totalElements}). */
    @Transactional(readOnly = true)
    public long countUniqueVisitors(UUID creatorUserId) {
        return visitRepository.countByCreatorUserId(creatorUserId);
    }

    private static CreatorProfileVisitItemDto toVisitItemDto(
            CreatorProfileVisit visit,
            Map<UUID, User> viewersById,
            Map<UUID, CreatorProfile> viewerProfilesByUserId
    ) {
        UUID viewerUserId = visit.getViewerUserId();
        boolean anonymous = viewerUserId == null;
        User viewer = anonymous ? null : viewersById.get(viewerUserId);
        CreatorProfile viewerProfile = anonymous ? null : viewerProfilesByUserId.get(viewerUserId);
        String viewerFullName = null;
        String viewerAvatarUrl = null;
        String viewerAppRole = null;
        if (!anonymous) {
            if (viewer != null && viewer.getFullName() != null && !viewer.getFullName().isBlank()) {
                viewerFullName = viewer.getFullName().trim();
            } else {
                viewerFullName = "User";
            }
            if (viewer != null) {
                viewerAvatarUrl = viewer.getAvatarUrl();
            }
            if (viewerProfile != null && viewerProfile.getAppRole() != null && !viewerProfile.getAppRole().isBlank()) {
                viewerAppRole = viewerProfile.getAppRole().trim();
            }
        }
        int visitCount = visit.getVisitCount() != null && visit.getVisitCount() > 0 ? visit.getVisitCount() : 1;
        return new CreatorProfileVisitItemDto(
                visit.getId(),
                visit.getViewedAt(),
                anonymous,
                viewerUserId,
                viewerFullName,
                viewerAvatarUrl,
                viewerAppRole,
                visitCount
        );
    }

    /**
     * Notify on every registered visit (including revisits by the same account).
     * Anonymous visits stay in the unique-visitor counter / Visitors list only.
     */
    private void notifyCreatorOfVisit(UUID viewerUserId, UUID creatorUserId) {
        if (viewerUserId == null) {
            return;
        }

        User viewer = userRepository.findByIdAndDeletedAtIsNull(viewerUserId).orElse(null);
        String viewerName = viewer != null && viewer.getFullName() != null && !viewer.getFullName().isBlank()
                ? viewer.getFullName().trim()
                : "A user";

        notificationService.createAndSend(
                creatorUserId,
                "CREATOR_PROFILE_VISIT",
                "Profile visit",
                viewerName + " visited your profile.",
                "PLATFORM",
                creatorUserId,
                viewerUserId
        );
    }

    private long uniqueVisitorCount(UUID creatorUserId, CreatorProfile profile) {
        long counted = visitRepository.countByCreatorUserId(creatorUserId);
        if (counted > 0) {
            return counted;
        }
        return profile.getProfileVisits() != null ? profile.getProfileVisits().longValue() : 0L;
    }

    private void syncDenormalizedCount(CreatorProfile profile, long count) {
        int safe = count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
        if (profile.getProfileVisits() == null || profile.getProfileVisits() != safe) {
            profile.setProfileVisits(safe);
            creatorProfileRepository.save(profile);
        }
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
}
