package com.plateforme.ecosystem.service;

import com.plateforme.ecosystem.dto.NicheRequestResponse;
import com.plateforme.ecosystem.dto.ValidateModelDto;
import com.plateforme.ecosystem.entity.NicheRequest;
import com.plateforme.ecosystem.entity.NicheStatus;
import com.plateforme.ecosystem.repository.NicheRequestRepository;
import com.plateforme.payment.service.PaymentService;
import com.plateforme.scheduler.repository.ScheduledConfigRepository;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.shared.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ValidationService {

    private final NicheRequestRepository nicheRequestRepository;
    private final EcosystemService ecosystemService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final ScheduledConfigRepository scheduledConfigRepository;

    public NicheRequestResponse validateModel(UUID requestId, UUID clientId, ValidateModelDto dto) {
        NicheRequest nr = nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)
                .orElseThrow(() -> new BusinessException("NICHE_REQUEST_NOT_FOUND",
                        "Demande introuvable"));

        if (nr.getStatus() != NicheStatus.PROPOSED) {
            throw new BusinessException("MODEL_NOT_AVAILABLE", "Modèle non encore disponible");
        }

        if (dto.accepted()) {
            nr.setStatus(NicheStatus.VALIDATED);
            nr.setValidatedAt(LocalDateTime.now());
            nicheRequestRepository.save(nr);
            String checkoutUrl = paymentService.createCheckoutSession(requestId, clientId);
            NicheRequest fresh = nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)
                    .orElse(nr);
            return ecosystemService.toResponse(fresh,
                    scheduledConfigRepository.findByNicheRequest_Id(fresh.getId()).orElse(null)
            ).withCheckoutUrl(checkoutUrl);
        }

        if (dto.rejectionReason() == null || dto.rejectionReason().isBlank()) {
            throw new BusinessException("REJECTION_REASON_REQUIRED", "La raison du refus est obligatoire");
        }

        nr.setStatus(NicheStatus.REJECTED);
        nr.setRejectionReason(dto.rejectionReason().trim());
        nr.setDeletedAt(LocalDateTime.now());
        nicheRequestRepository.save(nr);

        if (nr.getAgent() != null) {
            String code = nr.getUniqueCode() != null ? nr.getUniqueCode() : requestId.toString();
            notificationService.createAndSend(
                    nr.getAgent().getId(),
                    "DEMO_REJECTED",
                    "Validation model rejected",
                    "The client rejected the validation model (" + code + "): " + dto.rejectionReason(),
                    "PLATFORM",
                    requestId
            );
        }

        log.info("Demande rejetée id={} par client={}", requestId, clientId);

        return ecosystemService.toResponse(nr,
                scheduledConfigRepository.findByNicheRequest_Id(nr.getId()).orElse(null));
    }

    /**
     * Le client ignore l'étape de validation modèle (sans accepter/refuser la démo)
     * et passe directement au paiement.
     */
    public NicheRequestResponse skipModelValidation(UUID requestId, UUID clientId) {
        NicheRequest nr = nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)
                .orElseThrow(() -> new BusinessException("NICHE_REQUEST_NOT_FOUND",
                        "Demande introuvable"));

        if (nr.getStatus() == NicheStatus.PENDING) {
            if (!Boolean.TRUE.equals(nr.getBotConfirmed())) {
                throw new BusinessException("BOT_NOT_CONFIRMED",
                        "Confirmation bot requise avant de passer au paiement");
            }
        } else if (nr.getStatus() != NicheStatus.PROPOSED) {
            throw new BusinessException("CANNOT_SKIP_MODEL",
                    "Impossible d'ignorer la validation dans cet état");
        }

        nr.setStatus(NicheStatus.VALIDATED);
        nr.setValidatedAt(LocalDateTime.now());
        nicheRequestRepository.save(nr);

        log.info("Validation modèle ignorée id={} par client={}", requestId, clientId);

        return ecosystemService.toResponse(nr,
                scheduledConfigRepository.findByNicheRequest_Id(nr.getId()).orElse(null));
    }
}
