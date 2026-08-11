package com.plateforme.ecosystem.service;

import com.plateforme.ecosystem.dto.NicheRequestFormDto;
import com.plateforme.ecosystem.dto.NicheRequestResponse;
import com.plateforme.ecosystem.entity.NicheRequest;
import com.plateforme.ecosystem.entity.NicheStatus;
import com.plateforme.ecosystem.entity.PaymentStatus;
import com.plateforme.ecosystem.entity.RefType;
import com.plateforme.ecosystem.repository.NicheRequestRepository;
import com.plateforme.ecosystem.storage.StorageService;
import com.plateforme.scheduler.entity.ScheduledConfig;
import com.plateforme.scheduler.repository.ScheduledConfigRepository;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.shared.service.NotificationService;
import com.plateforme.shared.service.PlatformConfigService;
import com.plateforme.shared.util.UniqueCodeGenerator;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class EcosystemService {

    private static final Set<String> ALLOWED_PLATFORMS = Set.of(
            "INSTAGRAM", "TIKTOK", "YOUTUBE", "FACEBOOK", "TWITTER");

    private static final long MAX_REF_MP4_BYTES = 500L * 1024 * 1024;

    private final NicheRequestRepository nicheRequestRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final UniqueCodeGenerator uniqueCodeGenerator;
    private final PlatformConfigService platformConfigService;
    private final StorageService storageService;
    private final ScheduledConfigRepository scheduledConfigRepository;

    public NicheRequestResponse submitNicheRequest(UUID clientId, NicheRequestFormDto dto) {
        User client = userRepository.findByIdAndDeletedAtIsNull(clientId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND",
                        "Client introuvable : " + clientId));

        for (String p : dto.platforms()) {
            if (!ALLOWED_PLATFORMS.contains(p)) {
                throw new BusinessException("INVALID_PLATFORM", "Plateforme non autorisée : " + p);
            }
        }

        RefType refType = parseRefType(dto.refType());
        validateRefFields(refType, dto);

        int monthly = platformConfigService.calculateMonthlyAmount(dto.nbPostsPerWeek());
        String uniqueCode = uniqueCodeGenerator.generate();

        NicheRequest nr = new NicheRequest();
        nr.setClient(client);
        nr.setNicheTheme(dto.nicheTheme().trim());
        nr.setDescription(dto.description().trim());
        nr.setLanguage(dto.language().trim().toUpperCase(Locale.ROOT));
        nr.setNbPostsPerWeek((short) dto.nbPostsPerWeek());
        nr.setPlatforms(new ArrayList<>(dto.platforms()));
        nr.setRefType(refType);
        nr.setRefMctCode(dto.refMctCode() != null ? dto.refMctCode().trim() : null);
        nr.setRefExternalUrl(dto.refExternalUrl() != null ? dto.refExternalUrl().trim() : null);
        nr.setMonthlyAmountCents(monthly);
        nr.setUniqueCode(uniqueCode);
        nr.setStatus(NicheStatus.PENDING);
        nr.setBotConfirmed(false);
        nr.setPaymentStatus(PaymentStatus.UNPAID);

        nicheRequestRepository.save(nr);

        log.info("Demande niche créée id={} code={}", nr.getId(), uniqueCode);

        return toResponse(nr, scheduledConfigRepository.findByNicheRequest_Id(nr.getId()).orElse(null));
    }

    public String uploadRefFile(UUID clientId, UUID requestId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("FILE_REQUIRED", "Fichier requis");
        }
        String ct = file.getContentType();
        if (ct == null || !ct.equalsIgnoreCase("video/mp4")) {
            throw new BusinessException("INVALID_FILE_TYPE", "Seuls les fichiers MP4 sont acceptés");
        }
        if (file.getSize() > MAX_REF_MP4_BYTES) {
            throw new BusinessException("FILE_TOO_LARGE", "Taille maximale 500 Mo");
        }

        NicheRequest nr = nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)
                .orElseThrow(() -> new BusinessException("NICHE_REQUEST_NOT_FOUND",
                        "Demande introuvable ou accès refusé"));

        if (nr.getStatus() != NicheStatus.PENDING) {
            throw new BusinessException("NICHE_REQUEST_INVALID_STATE",
                    "Référence fichier uniquement en statut PENDING");
        }

        String key = "refs/" + requestId + "/" + UUID.randomUUID() + "-" + Objects.requireNonNullElse(file.getOriginalFilename(), "ref.mp4");
        try {
            String url = storageService.uploadPublicFile(key, file.getInputStream(), file.getSize(), ct);
            nr.setRefType(RefType.MP4);
            nr.setRefFileUrl(url);
            nicheRequestRepository.save(nr);
            return url;
        } catch (Exception e) {
            log.error("Échec upload référence : {}", e.getMessage());
            throw new BusinessException("UPLOAD_FAILED", "Impossible d'enregistrer le fichier");
        }
    }

    public NicheRequestResponse confirmBotChat(UUID requestId, UUID clientId) {
        NicheRequest nr = nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)
                .orElseThrow(() -> new BusinessException("NICHE_REQUEST_NOT_FOUND",
                        "Demande introuvable"));

        if (nr.getStatus() != NicheStatus.PENDING) {
            throw new BusinessException("NICHE_REQUEST_INVALID_STATE",
                    "Confirmation bot impossible dans cet état");
        }

        nr.setBotConfirmed(true);
        nr.setBotConfirmedAt(LocalDateTime.now());
        nicheRequestRepository.save(nr);

        String theme = nr.getNicheTheme() != null ? nr.getNicheTheme() : "your niche";

        notificationService.sendBulkToRole(
                "ROLE_AGENT",
                "NICHE_WAITING_VALIDATION",
                "Niche awaiting validation model",
                "The client confirmed \"" + theme + "\". Prepare the validation model.",
                nr.getId()
        );

        notificationService.createAndSend(
                clientId,
                "NICHE_PENDING_MODEL",
                "Request submitted",
                "Your niche \"" + theme + "\" is being processed. You will be notified when the validation model is ready.",
                "PLATFORM",
                nr.getId()
        );

        return toResponse(nr, scheduledConfigRepository.findByNicheRequest_Id(nr.getId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public Page<NicheRequestResponse> getMyRequests(UUID clientId, String status, Pageable pageable) {
        Page<NicheRequest> page;
        if (status == null || status.isBlank()) {
            page = nicheRequestRepository.findByClient_Id(clientId, pageable);
        } else {
            NicheStatus st = NicheStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            page = nicheRequestRepository.findByClient_IdAndStatus(clientId, st, pageable);
        }
        return page.map(n -> toResponse(n, scheduledConfigRepository.findByNicheRequest_Id(n.getId()).orElse(null)));
    }

    @Transactional(readOnly = true)
    public NicheRequestResponse getRequestDetail(UUID requestId, UUID clientId) {
        NicheRequest nr = nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)
                .orElseThrow(() -> new BusinessException("NICHE_REQUEST_NOT_FOUND",
                        "Demande introuvable"));
        return toResponse(nr, scheduledConfigRepository.findByNicheRequest_Id(nr.getId()).orElse(null));
    }

    public void cancelRequest(UUID requestId, UUID clientId) {
        NicheRequest nr = nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)
                .orElseThrow(() -> new BusinessException("NICHE_REQUEST_NOT_FOUND",
                        "Demande introuvable"));

        if (nr.getStatus() != NicheStatus.PENDING && nr.getStatus() != NicheStatus.PROPOSED) {
            throw new BusinessException("NICHE_REQUEST_CANNOT_CANCEL",
                    "Annulation possible uniquement pour PENDING ou PROPOSED");
        }

        nr.setDeletedAt(LocalDateTime.now());
        nr.setStatus(NicheStatus.CANCELLED);
        nicheRequestRepository.save(nr);
        log.info("Demande niche annulée id={}", requestId);
    }

    public NicheRequestResponse toResponse(NicheRequest n, ScheduledConfig cfg) {
        String refT = n.getRefType() != null ? n.getRefType().name() : null;
        String money = formatMoney(n.getMonthlyAmountCents());
        String next = computeNextStep(n, cfg);

        User client = n.getClient();
        User agent = n.getAgent();

        return new NicheRequestResponse(
                n.getId(),
                n.getUniqueCode(),
                n.getNicheTheme(),
                n.getDescription(),
                n.getLanguage(),
                n.getNbPostsPerWeek() != null ? n.getNbPostsPerWeek() : 0,
                n.getPlatforms() != null ? List.copyOf(n.getPlatforms()) : List.of(),
                refT,
                n.getRefMctCode(),
                n.getRefExternalUrl(),
                n.getRefFileUrl(),
                n.getMonthlyAmountCents(),
                money,
                n.getStatus().name(),
                n.getPaymentStatus() != null ? n.getPaymentStatus().name() : null,
                Boolean.TRUE.equals(n.getBotConfirmed()),
                n.getDemoContentUrl(),
                n.getAgentNotes(),
                n.getCreatedAt(),
                n.getUpdatedAt(),
                n.getActivatedAt(),
                next,
                null,
                n.getRejectionReason(),
                agent != null ? agent.getId() : null,
                client != null ? client.getEmail() : null,
                client != null ? client.getFullName() : null
        );
    }

    private String computeNextStep(NicheRequest n, ScheduledConfig cfg) {
        if (n.getStatus() == NicheStatus.CANCELLED || n.getStatus() == NicheStatus.REJECTED) {
            return n.getStatus().name();
        }
        if (n.getStatus() == NicheStatus.PENDING) {
            return Boolean.TRUE.equals(n.getBotConfirmed()) ? "WAITING_AGENT" : "BOT_CHAT";
        }
        if (n.getStatus() == NicheStatus.PROPOSED) {
            return "VALIDATE_MODEL";
        }
        if (n.getStatus() == NicheStatus.VALIDATED) {
            return "PAYMENT";
        }
        if (n.getStatus() == NicheStatus.ACTIVE) {
            if (cfg != null && cfg.getUpdatedAt() == null) {
                return "SCHEDULER";
            }
            return "ACTIVE";
        }
        return "PAYMENT";
    }

    private String formatMoney(Integer cents) {
        if (cents == null) {
            return null;
        }
        BigDecimal eur = BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return NumberFormat.getNumberInstance(Locale.FRANCE).format(eur) + " €";
    }

    private RefType parseRefType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return RefType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_REF_TYPE", "refType doit être MCT, URL ou MP4");
        }
    }

    private void validateRefFields(RefType refType, NicheRequestFormDto dto) {
        if (refType == null) {
            return;
        }
        switch (refType) {
            case MCT -> {
                String code = dto.refMctCode();
                if (code == null || !code.matches("MCT-[A-Z0-9]{4}")) {
                    throw new BusinessException("INVALID_MCT_CODE", "Code modèle invalide (format MCT-XXXX)");
                }
            }
            case URL -> {
                String url = dto.refExternalUrl();
                if (url == null || url.isBlank()) {
                    throw new BusinessException("URL_REQUIRED", "URL requise pour refType URL");
                }
                validateHttpUrl(url);
            }
            case MP4 -> {
                // fichier envoyé via endpoint dédié
            }
        }
    }

    private void validateHttpUrl(String url) {
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new BusinessException("INVALID_URL", "URL http ou https requise");
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_URL", "URL invalide");
        }
    }
}
