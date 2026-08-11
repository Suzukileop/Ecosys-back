package com.plateforme.payment.service;

import com.plateforme.ecosystem.entity.NicheRequest;
import com.plateforme.ecosystem.entity.NicheStatus;
import com.plateforme.ecosystem.entity.PaymentStatus;
import com.plateforme.ecosystem.repository.NicheRequestRepository;
import com.plateforme.payment.client.VanillaPayClient;
import com.plateforme.payment.client.VanillaPayClient.VpiPaymentDetails;
import com.plateforme.payment.client.VanillaPayClient.VpiPaymentInitRequest;
import com.plateforme.scheduler.entity.ScheduledConfig;
import com.plateforme.scheduler.repository.ScheduledConfigRepository;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.shared.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    @Value("${app.payment.simulate-without-vpi:${app.payment.simulate-without-stripe:false}}")
    private boolean simulateWithoutVpi;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.backend-public-url:http://localhost:8080}")
    private String backendPublicUrl;

    @Value("${vpi.payment-method:mobile_money}")
    private String paymentMethod;

    @Value("${vpi.currency:MGA}")
    private String currency;

    @Value("${vpi.eur-to-mga-rate:4920}")
    private long eurToMgaRate;

    private final VanillaPayClient vanillaPayClient;
    private final NicheRequestRepository nicheRequestRepository;
    private final ScheduledConfigRepository scheduledConfigRepository;
    private final NotificationService notificationService;

    @Transactional
    public String createCheckoutSession(UUID requestId, UUID clientId) {
        boolean useSimulation = simulateWithoutVpi && !vanillaPayClient.isConfigured();
        if (!useSimulation) {
            ensureVpiConfigured();
        }

        NicheRequest nr = loadValidatedRequest(requestId, clientId);

        if (useSimulation) {
            return completeCheckoutInSimulation(requestId, nr);
        }

        String successUrl = ecosystemPaymentUrl(requestId, "success");
        String cancelUrl = ecosystemPaymentUrl(requestId, "cancelled");
        String callbackUrl = backendPublicUrl + "/api/payments/webhook/vpi";
        long amount = resolveVpiAmount(nr);

        VpiPaymentInitRequest initRequest = new VpiPaymentInitRequest(
                amount,
                requestId.toString(),
                "Ecosystem-" + (nr.getUniqueCode() != null ? nr.getUniqueCode() : requestId),
                currency,
                paymentMethod,
                callbackUrl,
                successUrl
        );

        var result = vanillaPayClient.initiatePayment(initRequest);
        nr.setVpiPaymentId(result.id());
        nr.setVpiReference(requestId.toString());
        nr.setPaymentStatus(PaymentStatus.PENDING_PAYMENT);
        nicheRequestRepository.save(nr);
        log.info("Paiement VPI initié nicheRequest={} vpiId={}", requestId, result.id());
        return result.url();
    }

    @Transactional
    public void confirmEcosystemPayment(UUID requestId, UUID clientId) {
        NicheRequest nr = nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)
                .orElseThrow(() -> new BusinessException("NICHE_REQUEST_NOT_FOUND", "Demande introuvable"));

        if (nr.getPaymentStatus() == PaymentStatus.PAID || nr.getStatus() == NicheStatus.ACTIVE) {
            return;
        }
        if (nr.getVpiPaymentId() == null || nr.getVpiPaymentId().isBlank()) {
            throw new BusinessException("PAYMENT_NOT_STARTED", "Aucun paiement en cours");
        }

        VpiPaymentDetails details = vanillaPayClient.getPaymentStatus(nr.getVpiPaymentId(), paymentMethod);
        applyPaymentDetails(nr, details);
    }

    @Transactional
    public void handleVpiWebhook(String vpiSignature, Map<String, String> body) {
        ensureVpiConfigured();

        Map<String, String> payload = new LinkedHashMap<>(body);
        if (!vanillaPayClient.validateWebhookSignature(vpiSignature, payload)) {
            log.warn("Signature webhook VPI invalide");
            throw new BusinessException("VPI_SIGNATURE_INVALID", "Signature webhook invalide");
        }

        VpiPaymentDetails details = vanillaPayClient.mapWebhookPayload(payload);
        UUID requestId = parseRequestId(details.reference());
        if (requestId == null) {
            log.warn("Webhook VPI sans référence niche valide : {}", details.reference());
            return;
        }

        NicheRequest nr = nicheRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("NICHE_REQUEST_NOT_FOUND",
                        "Demande introuvable : " + requestId));

        applyPaymentDetails(nr, details);
    }

    @Transactional
    public void activateEcosystem(UUID requestId) {
        NicheRequest nr = nicheRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("NICHE_REQUEST_NOT_FOUND",
                        "Demande introuvable : " + requestId));

        nr.setStatus(NicheStatus.ACTIVE);
        nr.setActivatedAt(LocalDateTime.now());
        nicheRequestRepository.save(nr);

        if (scheduledConfigRepository.findByNicheRequest_Id(nr.getId()).isEmpty()) {
            ScheduledConfig cfg = new ScheduledConfig();
            cfg.setNicheRequest(nr);
            cfg.setClient(nr.getClient());
            cfg.setPublicationSlots(new ArrayList<>());
            cfg.setPlatforms(nr.getPlatforms() != null ? new ArrayList<>(nr.getPlatforms()) : new ArrayList<>());
            scheduledConfigRepository.save(cfg);
        }

        notificationService.createAndSend(
                nr.getClient().getId(),
                "ECOSYSTEM_ACTIVE",
                "Ecosystem activated",
                "Your ecosystem \"" + nr.getNicheTheme() + "\" is active! Set up your publishing schedule.",
                "BOTH",
                nr.getId()
        );

        UUID assignedAgentId = nr.getAgent() != null ? nr.getAgent().getId() : null;
        notificationService.notifyAgentOrAll(
                assignedAgentId,
                "NICHE_ACTIVATED",
                "Niche activated by client",
                "The client activated the ecosystem \"" + nr.getNicheTheme() + "\". You can deliver content.",
                nr.getId()
        );

        log.info("Écosystème activé pour nicheRequest={}", requestId);
    }

    private void applyPaymentDetails(NicheRequest nr, VpiPaymentDetails details) {
        if (details.vpiReference() != null && !details.vpiReference().isBlank()) {
            nr.setVpiReference(details.vpiReference());
        }
        if (details.isSuccessful()) {
            if (nr.getPaymentStatus() != PaymentStatus.PAID) {
                nr.setPaymentStatus(PaymentStatus.PAID);
                nr.setPaidAt(LocalDateTime.now());
                nicheRequestRepository.save(nr);
                activateEcosystem(nr.getId());
            }
            return;
        }
        if (details.isFailed()) {
            nr.setPaymentStatus(PaymentStatus.FAILED);
            nicheRequestRepository.save(nr);
            notificationService.createAndSend(
                    nr.getClient().getId(),
                    "PAYMENT_FAILED",
                    "Payment failed",
                    "Your ecosystem subscription payment failed. Please try again.",
                    "BOTH",
                    nr.getId()
            );
            log.warn("Paiement VPI échoué pour niche {}", nr.getId());
        }
    }

    private String completeCheckoutInSimulation(UUID requestId, NicheRequest nr) {
        String successUrl = ecosystemPaymentUrl(requestId, "success") + "&simulated=true";
        nr.setVpiPaymentId("sim_vpi_" + requestId);
        nr.setPaymentStatus(PaymentStatus.PAID);
        nr.setPaidAt(LocalDateTime.now());
        nr.setVpiReference("sim_ref_" + requestId);
        nicheRequestRepository.save(nr);
        log.warn(
                "Paiement SIMULÉ (app.payment.simulate-without-vpi=true, VPI absent) — nicheRequest={} — ne pas utiliser en production",
                requestId);
        activateEcosystem(requestId);
        return successUrl;
    }

    private NicheRequest loadValidatedRequest(UUID requestId, UUID clientId) {
        NicheRequest nr = nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)
                .orElseThrow(() -> new BusinessException("NICHE_REQUEST_NOT_FOUND", "Demande introuvable"));

        if (nr.getStatus() != NicheStatus.VALIDATED) {
            throw new BusinessException("NICHE_NOT_VALIDATED", "La demande doit être validée avant paiement");
        }
        if (nr.getMonthlyAmountCents() == null || nr.getMonthlyAmountCents() <= 0) {
            throw new BusinessException("INVALID_AMOUNT", "Montant mensuel invalide");
        }
        return nr;
    }

    private long resolveVpiAmount(NicheRequest nr) {
        long eurWhole = Math.max(1, Math.round(nr.getMonthlyAmountCents() / 100.0));
        if ("EUR".equalsIgnoreCase(currency)) {
            return eurWhole;
        }
        return Math.max(100, Math.round(eurWhole * eurToMgaRate));
    }

    private String ecosystemPaymentUrl(UUID requestId, String outcome) {
        return frontendUrl + "/dashboard/ecosystem/" + requestId + "?payment=" + outcome;
    }

    private void ensureVpiConfigured() {
        if (!vanillaPayClient.isConfigured()) {
            throw new BusinessException("VPI_NOT_CONFIGURED", "Vanilla Pay International non configuré");
        }
    }

    private static UUID parseRequestId(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(reference.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
