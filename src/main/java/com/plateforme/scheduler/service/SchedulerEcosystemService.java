package com.plateforme.scheduler.service;

import com.plateforme.ecosystem.entity.NicheRequest;
import com.plateforme.ecosystem.entity.NicheStatus;
import com.plateforme.ecosystem.repository.NicheRequestRepository;
import com.plateforme.scheduler.dto.NicheRefDto;
import com.plateforme.scheduler.dto.PublicationSlotDto;
import com.plateforme.scheduler.dto.ScheduledConfigDto;
import com.plateforme.scheduler.dto.ScheduledPostRequest;
import com.plateforme.scheduler.dto.ScheduledPostResponse;
import com.plateforme.scheduler.entity.ContentType;
import com.plateforme.scheduler.entity.PostStatus;
import com.plateforme.scheduler.entity.PublicationAnalytics;
import com.plateforme.scheduler.entity.ScheduledConfig;
import com.plateforme.scheduler.entity.ScheduledPost;
import com.plateforme.scheduler.repository.PublicationAnalyticsRepository;
import com.plateforme.scheduler.repository.ScheduledConfigRepository;
import com.plateforme.scheduler.repository.ScheduledPostRepository;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.shared.service.NotificationService;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulerEcosystemService {

    private final ScheduledPostRepository scheduledPostRepository;
    private final PublicationAnalyticsRepository publicationAnalyticsRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final NicheRequestRepository nicheRequestRepository;
    private final ScheduledConfigRepository scheduledConfigRepository;

    @Transactional
    public ScheduledConfigDto updateScheduledConfig(UUID clientId, UUID requestId, ScheduledConfigDto dto) {
        if (!Objects.equals(dto.nicheRequestId(), requestId)) {
            throw new BusinessException("REQUEST_ID_MISMATCH", "The niche identifier does not match.");
        }
        NicheRequest nr = nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)
                .orElseThrow(() -> new BusinessException("NICHE_REQUEST_NOT_FOUND",
                        "Request not found."));

        if (nr.getStatus() != NicheStatus.ACTIVE) {
            throw new BusinessException("NICHE_NOT_ACTIVE",
                    "The schedule can only be edited for an active niche.");
        }

        ScheduledConfig cfg = scheduledConfigRepository.findByNicheRequest_IdAndClient_Id(requestId, clientId)
                .orElseThrow(() -> new BusinessException("SCHEDULE_CONFIG_NOT_FOUND",
                        "Schedule configuration not found."));

        validatePublicationSlots(nr, dto.publicationSlots());

        cfg.setPublicationSlots(new ArrayList<>(dto.publicationSlots()));
        scheduledConfigRepository.save(cfg);

        return toConfigDto(cfg);
    }

    @Transactional(readOnly = true)
    public ScheduledConfigDto getMyScheduledConfig(UUID clientId, UUID requestId) {
        nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)
                .orElseThrow(() -> new BusinessException("NICHE_REQUEST_NOT_FOUND",
                        "Request not found."));

        ScheduledConfig cfg = scheduledConfigRepository.findByNicheRequest_IdAndClient_Id(requestId, clientId)
                .orElseThrow(() -> new BusinessException("SCHEDULE_CONFIG_NOT_FOUND",
                        "Schedule configuration not found."));

        return toConfigDto(cfg);
    }

    @Transactional
    public ScheduledPostResponse scheduleManualPost(UUID clientId, ScheduledPostRequest req) {
        User client = userRepository.findByIdAndDeletedAtIsNull(clientId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND",
                        "User not found."));

        NicheRequest nr = nicheRequestRepository.findByIdAndClient_Id(req.nicheRequestId(), clientId)
                .orElseThrow(() -> new BusinessException("NICHE_REQUEST_NOT_FOUND",
                        "Request not found."));

        if (nr.getStatus() != NicheStatus.ACTIVE) {
            throw new BusinessException("NICHE_NOT_ACTIVE", "The niche must be active to schedule posts.");
        }

        ScheduledConfig cfg = scheduledConfigRepository.findByNicheRequest_IdAndClient_Id(req.nicheRequestId(), clientId)
                .orElseThrow(() -> new BusinessException("SCHEDULE_CONFIG_NOT_FOUND",
                        "Schedule configuration not found."));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime minSchedule = now.plusMinutes(2);
        if (!req.scheduledAt().isAfter(minSchedule)) {
            throw new BusinessException("SCHEDULE_TOO_SOON",
                    "The publish time must be at least 2 minutes in the future.");
        }

        ContentType contentType = req.contentType();
        if (contentType == ContentType.EXTERNAL_URL) {
            if (req.contentUrl() == null || req.contentUrl().isBlank()) {
                throw new BusinessException("CONTENT_URL_REQUIRED",
                        "A content URL is required for an external source.");
            }
            validateHttpUrl(req.contentUrl());
        }

        ScheduledPost post = new ScheduledPost();
        post.setClient(client);
        post.setScheduledConfig(cfg);
        post.setNicheRequest(nr);
        post.setPlatform(req.platform());
        post.setContentUrl(req.contentUrl());
        post.setContentType(contentType);
        post.setCaption(req.caption());
        post.setNicheRef(req.nicheRef());
        post.setScheduledAt(req.scheduledAt());
        post.setStatus(PostStatus.SCHEDULED);

        post = scheduledPostRepository.save(post);
        log.info("Publication planifiée id={} client={} niche={}", post.getId(), clientId, req.nicheRequestId());

        return toResponse(post);
    }

    @Transactional(readOnly = true)
    public Page<ScheduledPostResponse> getMyPosts(UUID clientId, String status, Pageable pageable) {
        if (status == null || status.isBlank()) {
            return scheduledPostRepository.findByClient_Id(clientId, pageable).map(this::toResponse);
        }
        PostStatus postStatus;
        try {
            postStatus = PostStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_POST_STATUS", "Invalid post status.");
        }
        return scheduledPostRepository.findByClient_IdAndStatus(clientId, postStatus, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ScheduledPostResponse> getPostsByNicheRequest(UUID clientId, UUID requestId, Pageable pageable) {
        nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)
                .orElseThrow(() -> new BusinessException("NICHE_REQUEST_NOT_FOUND",
                        "Request not found."));

        return scheduledPostRepository
                .findByNicheRequest_IdAndClient_Id(requestId, clientId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public void cancelPost(UUID clientId, UUID postId) {
        ScheduledPost post = scheduledPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("SCHEDULED_POST_NOT_FOUND",
                        "Scheduled post not found."));

        UUID ownerId = post.getClient() != null ? post.getClient().getId() : null;
        if (!Objects.equals(ownerId, clientId)) {
            throw new AccessDeniedException("Cette publication n'appartient pas à l'utilisateur courant");
        }
        if (post.getStatus() != PostStatus.SCHEDULED) {
            throw new BusinessException("POST_ALREADY_PROCESSED", "This post has already been processed.");
        }

        post.setStatus(PostStatus.CANCELLED);
        scheduledPostRepository.save(post);
        log.info("Publication annulée id={} par client={}", postId, clientId);
    }

    public void processScheduledPosts() {
        List<ScheduledPost> due = scheduledPostRepository
                .findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(PostStatus.SCHEDULED, LocalDateTime.now());

        int processed = 0;
        int successes = 0;
        int failures = 0;

        for (ScheduledPost post : due) {
            processed++;
            try {
                processPost(post.getId());
                successes++;
            } catch (Exception ex) {
                failures++;
                handlePostFailure(post.getId(), ex.getMessage());
            }
        }

        log.info("Job publication écosystème : {} post(s) dus, {} succès, {} erreurs",
                processed, successes, failures);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processPost(UUID postId) {
        ScheduledPost post = scheduledPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("SCHEDULED_POST_NOT_FOUND",
                        "Scheduled post not found."));

        log.info("Traitement publication postId={} vers {}", postId, post.getPlatform());

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("PUBLICATION_INTERRUPTED", "Publication interrompue");
        }

        log.info("Publication simulée vers {}", post.getPlatform());

        post.setStatus(PostStatus.PUBLISHED);
        post.setPublishedAt(LocalDateTime.now());
        scheduledPostRepository.save(post);

        PublicationAnalytics analytics = new PublicationAnalytics();
        analytics.setPost(post);
        analytics.setPlatform(post.getPlatform());
        analytics.setViews(0);
        analytics.setLikes(0);
        analytics.setShares(0);
        analytics.setComments(0);
        publicationAnalyticsRepository.save(analytics);

        UUID clientId = post.getClient().getId();
        notificationService.createAndSend(
                clientId,
                "POST_PUBLISHED",
                "Published successfully",
                "Your content was published on " + post.getPlatform(),
                "BOTH",
                postId
        );

        log.info("Publication réussie postId={}", postId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handlePostFailure(UUID postId, String errorMsg) {
        ScheduledPost post = scheduledPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("SCHEDULED_POST_NOT_FOUND",
                        "Scheduled post not found."));

        post.setStatus(PostStatus.FAILED);
        post.setErrorMessage(errorMsg);
        scheduledPostRepository.save(post);

        UUID clientId = post.getClient().getId();
        notificationService.createAndSend(
                clientId,
                "POST_FAILED",
                "Publication failed",
                "Failed to publish on " + post.getPlatform() + ": " + errorMsg,
                "PLATFORM",
                postId
        );

        log.warn("Échec publication postId={} : {}", postId, errorMsg);
    }

    @Transactional(readOnly = true)
    public List<NicheRefDto> getValidatedNichesForClient(UUID clientId) {
        return nicheRequestRepository.findByClient_IdAndStatus(clientId, NicheStatus.ACTIVE, Pageable.unpaged())
                .getContent()
                .stream()
                .map(nr -> new NicheRefDto(nr.getUniqueCode(), nr.getNicheTheme()))
                .filter(n -> n.nicheCode() != null)
                .toList();
    }

    private ScheduledConfigDto toConfigDto(ScheduledConfig cfg) {
        List<PublicationSlotDto> slots = cfg.getPublicationSlots() != null
                ? List.copyOf(cfg.getPublicationSlots())
                : List.of();
        return new ScheduledConfigDto(cfg.getNicheRequest().getId(), slots);
    }

    private void validatePublicationSlots(NicheRequest nr, List<PublicationSlotDto> slots) {
        int expected = nr.getNbPostsPerWeek() != null ? nr.getNbPostsPerWeek().intValue() : 0;
        if (expected <= 0) {
            throw new BusinessException("NICHE_POSTS_INVALID",
                    "Invalid number of posts per week for this request.");
        }
        if (slots == null || slots.size() != expected) {
            throw new BusinessException(
                    "SLOT_COUNT_MISMATCH",
                    "Set exactly " + expected + " time slot(s) (day and time), one for each weekly post.");
        }
        Set<String> seen = new HashSet<>();
        for (PublicationSlotDto s : slots) {
            String key = s.dayOfWeek() + "|" + s.time();
            if (!seen.add(key)) {
                throw new BusinessException(
                        "DUPLICATE_PUBLICATION_SLOT",
                        "Duplicate time slot: day " + s.dayOfWeek() + " at " + s.time() + ".");
            }
        }
    }

    private ScheduledPostResponse toResponse(ScheduledPost p) {
        return new ScheduledPostResponse(
                p.getId(),
                p.getPlatform(),
                p.getContentUrl(),
                p.getContentType(),
                p.getCaption(),
                p.getNicheRef(),
                p.getDeliveryNumber(),
                p.getScheduledAt(),
                p.getStatus(),
                p.getPublishedAt(),
                p.getErrorMessage(),
                p.getCreatedAt()
        );
    }

    private void validateHttpUrl(String url) {
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new BusinessException("INVALID_CONTENT_URL", "The content URL must start with http or https.");
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_CONTENT_URL", "Invalid content URL.");
        }
    }
}
