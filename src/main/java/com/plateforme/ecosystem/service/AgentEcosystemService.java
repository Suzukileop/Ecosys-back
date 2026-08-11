package com.plateforme.ecosystem.service;

import com.plateforme.ecosystem.dto.AgentDeliverContentDto;
import com.plateforme.ecosystem.dto.AgentProposeDto;
import com.plateforme.ecosystem.dto.NicheRequestResponse;
import com.plateforme.ecosystem.entity.NicheRequest;
import com.plateforme.ecosystem.entity.NicheStatus;
import com.plateforme.ecosystem.repository.NicheRequestRepository;
import com.plateforme.ecosystem.storage.StorageService;
import com.plateforme.ecosystem.storage.StorageObjectKeys;
import com.plateforme.scheduler.dto.ScheduledPostResponse;
import com.plateforme.scheduler.entity.ContentType;
import com.plateforme.scheduler.entity.Platform;
import com.plateforme.scheduler.entity.PostStatus;
import com.plateforme.scheduler.entity.ScheduledConfig;
import com.plateforme.scheduler.entity.ScheduledPost;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AgentEcosystemService {

    private static final long MAX_DEMO_BYTES = 500L * 1024 * 1024;
    private static final long MAX_DELIVERY_BYTES = 500L * 1024 * 1024;

    private final NicheRequestRepository nicheRequestRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EcosystemService ecosystemService;
    private final StorageService storageService;
    private final ScheduledConfigRepository scheduledConfigRepository;
    private final ScheduledPostRepository scheduledPostRepository;

    @Transactional(readOnly = true)
    public Page<NicheRequestResponse> getPendingRequests(Pageable pageable) {
        return nicheRequestRepository
                .findByStatusAndBotConfirmedIsTrueOrderByCreatedAtAsc(NicheStatus.PENDING, pageable)
                .map(n -> ecosystemService.toResponse(n,
                        scheduledConfigRepository.findByNicheRequest_Id(n.getId()).orElse(null)));
    }

    @Transactional(readOnly = true)
    public NicheRequestResponse getRequestForAgent(UUID requestId) {
        NicheRequest nr = nicheRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("NICHE_REQUEST_NOT_FOUND",
                        "Demande introuvable : " + requestId));
        return ecosystemService.toResponse(nr,
                scheduledConfigRepository.findByNicheRequest_Id(nr.getId()).orElse(null));
    }

    public NicheRequestResponse proposeModel(UUID requestId, UUID agentId, AgentProposeDto dto) {
        NicheRequest nr = nicheRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("NICHE_REQUEST_NOT_FOUND",
                        "Demande introuvable : " + requestId));

        if (nr.getStatus() != NicheStatus.PENDING || !Boolean.TRUE.equals(nr.getBotConfirmed())) {
            throw new BusinessException("NICHE_REQUEST_INVALID_STATE",
                    "Proposition impossible : la demande doit être PENDING avec bot confirmé");
        }

        User agent = userRepository.findByIdAndDeletedAtIsNull(agentId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Agent introuvable : " + agentId));

        nr.setAgent(agent);
        nr.setDemoContentUrl(dto.demoContentUrl().trim());
        nr.setAgentNotes(dto.agentNotes());
        nr.setStatus(NicheStatus.PROPOSED);
        nr.setProposedAt(LocalDateTime.now());
        nicheRequestRepository.save(nr);

        notificationService.createAndSend(
                nr.getClient().getId(),
                "DEMO_READY",
                "Validation model ready",
                "Your agent published the validation model for \"" + nr.getNicheTheme() + "\". Review and approve it.",
                "BOTH",
                requestId
        );

        log.info("Agent={} a proposé un modèle pour nicheRequest={}", agentId, requestId);

        return ecosystemService.toResponse(nr,
                scheduledConfigRepository.findByNicheRequest_Id(nr.getId()).orElse(null));
    }

    public String uploadDemoContent(UUID requestId, UUID agentId, MultipartFile file) {
        User agent = userRepository.findByIdAndDeletedAtIsNull(agentId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Utilisateur introuvable"));

        boolean allowedRole = agent.getRoles().stream()
                .anyMatch(r -> Objects.equals(r.getName(), "ROLE_AGENT") || Objects.equals(r.getName(), "ROLE_ADMIN"));
        if (!allowedRole) {
            throw new BusinessException("AGENT_ROLE_REQUIRED", "Rôle agent ou admin requis");
        }

        if (file == null || file.isEmpty()) {
            throw new BusinessException("FILE_REQUIRED", "Fichier requis");
        }
        if (file.getSize() > MAX_DEMO_BYTES) {
            throw new BusinessException("FILE_TOO_LARGE", "Taille maximale 500 Mo");
        }

        NicheRequest nr = nicheRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("NICHE_REQUEST_NOT_FOUND",
                        "Demande introuvable : " + requestId));

        if (nr.getStatus() != NicheStatus.PENDING || !Boolean.TRUE.equals(nr.getBotConfirmed())) {
            throw new BusinessException("NICHE_REQUEST_INVALID_STATE",
                    "Upload démo impossible dans cet état");
        }

        String ct = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        String key = StorageObjectKeys.uniqueObjectKey("demos", requestId, file.getOriginalFilename());
        try {
            String url = storageService.uploadPublicFile(key, file.getInputStream(), file.getSize(), ct);
            nr.setDemoContentUrl(url);
            nicheRequestRepository.save(nr);
            return url;
        } catch (Exception e) {
            log.error("Upload démo échoué : {}", e.getMessage());
            throw new BusinessException("UPLOAD_FAILED", "Impossible d'enregistrer le fichier de démonstration");
        }
    }

    public String uploadModelVideo(UUID requestId, UUID agentId, MultipartFile file) {
        User agent = userRepository.findByIdAndDeletedAtIsNull(agentId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Utilisateur introuvable"));

        boolean allowedRole = agent.getRoles().stream()
                .anyMatch(r -> Objects.equals(r.getName(), "ROLE_AGENT") || Objects.equals(r.getName(), "ROLE_ADMIN"));
        if (!allowedRole) {
            throw new BusinessException("AGENT_ROLE_REQUIRED", "Rôle agent ou admin requis");
        }

        if (file == null || file.isEmpty()) {
            throw new BusinessException("FILE_REQUIRED", "Fichier requis");
        }

        NicheRequest nr = nicheRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("NICHE_REQUEST_NOT_FOUND",
                        "Demande introuvable : " + requestId));

        String key = "models/" + requestId + "/" + UUID.randomUUID() + ".mp4";
        try {
            String url = storageService.uploadFile(file, key);
            nr.setModelVideoUrl(url);
            nicheRequestRepository.save(nr);
            log.info("Vidéo modèle uploadée requestId={} url={}", requestId, url);
            return url;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Upload vidéo modèle échoué : {}", e.getMessage());
            throw new BusinessException("UPLOAD_FAILED", "Impossible d'enregistrer la vidéo modèle");
        }
    }

    @Transactional(readOnly = true)
    public Page<NicheRequestResponse> getActiveNiches(Pageable pageable) {
        return nicheRequestRepository
                .findByStatusOrderByActivatedAtDesc(NicheStatus.ACTIVE, pageable)
                .map(n -> ecosystemService.toResponse(n,
                        scheduledConfigRepository.findByNicheRequest_Id(n.getId()).orElse(null)));
    }

    @Transactional(readOnly = true)
    public Page<ScheduledPostResponse> getDeliveredContent(UUID requestId, UUID agentId, Pageable pageable) {
        assertAgentRole(agentId);
        NicheRequest nr = loadActiveNicheForDelivery(requestId);
        UUID clientId = nr.getClient().getId();
        return scheduledPostRepository
                .findByNicheRequest_IdAndClient_Id(requestId, clientId, pageable)
                .map(this::toPostResponse);
    }

    public ScheduledPostResponse deliverContent(
            UUID requestId,
            UUID agentId,
            MultipartFile file,
            AgentDeliverContentDto dto) {
        assertAgentRole(agentId);
        NicheRequest nr = loadActiveNicheForDelivery(requestId);
        validatePlatformForNiche(nr, dto.platform());

        if (file == null || file.isEmpty()) {
            throw new BusinessException("FILE_REQUIRED", "Fichier requis");
        }
        if (file.getSize() > MAX_DELIVERY_BYTES) {
            throw new BusinessException("FILE_TOO_LARGE", "Taille maximale 500 Mo");
        }

        String ct = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        String key = StorageObjectKeys.uniqueObjectKey("niche-deliveries", requestId, file.getOriginalFilename());

        String contentUrl;
        try {
            contentUrl = storageService.uploadPublicFile(key, file.getInputStream(), file.getSize(), ct);
        } catch (Exception e) {
            log.error("Upload contenu niche échoué : {}", e.getMessage());
            throw new BusinessException("UPLOAD_FAILED", "Impossible d'enregistrer le contenu");
        }

        ScheduledConfig cfg = scheduledConfigRepository.findByNicheRequest_Id(nr.getId())
                .orElseThrow(() -> new BusinessException("SCHEDULE_CONFIG_NOT_FOUND",
                        "Configuration planificateur introuvable"));

        LocalDateTime now = LocalDateTime.now();
        int deliveryNumber = scheduledPostRepository.findMaxDeliveryNumberByNicheRequestId(requestId) + 1;
        String defaultCaption = "Content #" + deliveryNumber;

        ScheduledPost post = new ScheduledPost();
        post.setClient(nr.getClient());
        post.setScheduledConfig(cfg);
        post.setNicheRequest(nr);
        post.setPlatform(dto.platform());
        post.setContentUrl(contentUrl);
        post.setContentType(ContentType.UPLOADED);
        post.setDeliveryNumber(deliveryNumber);
        post.setCaption(dto.caption() != null && !dto.caption().isBlank()
                ? dto.caption().trim()
                : defaultCaption);
        post.setNicheRef(nr.getUniqueCode());
        post.setScheduledAt(now);
        post.setStatus(PostStatus.PUBLISHED);
        post.setPublishedAt(now);

        post = scheduledPostRepository.save(post);

        notificationService.createAndSend(
                nr.getClient().getId(),
                "CONTENT_DELIVERED",
                "New content available",
                "Your agent delivered " + defaultCaption + " for \"" + nr.getNicheTheme() + "\".",
                "BOTH",
                requestId,
                post.getId()
        );

        log.info("Agent={} a livré un contenu pour nicheRequest={} postId={}", agentId, requestId, post.getId());
        return toPostResponse(post);
    }

    private NicheRequest loadActiveNicheForDelivery(UUID requestId) {
        NicheRequest nr = nicheRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("NICHE_REQUEST_NOT_FOUND",
                        "Demande introuvable : " + requestId));
        if (nr.getStatus() != NicheStatus.ACTIVE) {
            throw new BusinessException("NICHE_NOT_ACTIVE",
                    "Seules les niches actives acceptent de nouveaux contenus");
        }
        return nr;
    }

    private void validatePlatformForNiche(NicheRequest nr, Platform platform) {
        if (nr.getPlatforms() == null || nr.getPlatforms().isEmpty()) {
            return;
        }
        String platformName = platform.name();
        boolean allowed = nr.getPlatforms().stream()
                .anyMatch(p -> Objects.equals(p, platformName));
        if (!allowed) {
            throw new BusinessException("INVALID_PLATFORM",
                    "Plateforme non autorisée pour cette niche : " + platformName);
        }
    }

    private void assertAgentRole(UUID agentId) {
        User agent = userRepository.findByIdAndDeletedAtIsNull(agentId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Utilisateur introuvable"));
        boolean allowedRole = agent.getRoles().stream()
                .anyMatch(r -> Objects.equals(r.getName(), "ROLE_AGENT")
                        || Objects.equals(r.getName(), "ROLE_ADMIN"));
        if (!allowedRole) {
            throw new BusinessException("AGENT_ROLE_REQUIRED", "Rôle agent ou admin requis");
        }
    }

    private ScheduledPostResponse toPostResponse(ScheduledPost p) {
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
}
