package com.plateforme.ecosystem.service;

import com.plateforme.ecosystem.dto.ValidateModelDto;
import com.plateforme.ecosystem.entity.NicheRequest;
import com.plateforme.ecosystem.entity.NicheStatus;
import com.plateforme.ecosystem.repository.NicheRequestRepository;
import com.plateforme.payment.service.PaymentService;
import com.plateforme.scheduler.repository.ScheduledConfigRepository;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.shared.service.NotificationService;
import com.plateforme.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValidationServiceTest {

    @Mock
    private NicheRequestRepository nicheRequestRepository;

    @Mock
    private EcosystemService ecosystemService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ScheduledConfigRepository scheduledConfigRepository;

    @InjectMocks
    private ValidationService validationService;

    private User client;
    private UUID clientId;
    private UUID requestId;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        requestId = UUID.randomUUID();
        client = new User();
        client.setId(clientId);
        client.setEmail("c@test.com");
    }

    @Test
    @DisplayName("validateModel accepted → VALIDATED + checkout")
    void validateModel_accepted() {
        NicheRequest nr = new NicheRequest();
        nr.setClient(client);
        nr.setStatus(NicheStatus.PROPOSED);
        when(nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)).thenReturn(Optional.of(nr));
        when(paymentService.createCheckoutSession(requestId, clientId)).thenReturn("https://checkout.test");
        when(nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)).thenReturn(Optional.of(nr));
        when(scheduledConfigRepository.findByNicheRequest_Id(any())).thenReturn(Optional.empty());
        when(ecosystemService.toResponse(any(), any())).thenReturn(
                new com.plateforme.ecosystem.dto.NicheRequestResponse(
                        requestId, "MCT-X", "T", "D", "FR", 2, java.util.List.of("TIKTOK"),
                        null, null, null, null, 1000, "10,00 €", "VALIDATED", "PENDING_PAYMENT",
                        true, null, null, null, null, null, "PAYMENT", null, null, null, "e", "n"
                ));

        var res = validationService.validateModel(requestId, clientId, new ValidateModelDto(true, null));

        assertThat(nr.getStatus()).isEqualTo(NicheStatus.VALIDATED);
        verify(paymentService).createCheckoutSession(requestId, clientId);
        assertThat(res.checkoutUrl()).isEqualTo("https://checkout.test");
    }

    @Test
    @DisplayName("validateModel refused → REJECTED + notif agent")
    void validateModel_refused() {
        User agent = new User();
        agent.setId(UUID.randomUUID());
        NicheRequest nr = new NicheRequest();
        nr.setClient(client);
        nr.setAgent(agent);
        nr.setUniqueCode("MCT-RR01");
        nr.setStatus(NicheStatus.PROPOSED);
        when(nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)).thenReturn(Optional.of(nr));
        when(scheduledConfigRepository.findByNicheRequest_Id(any())).thenReturn(Optional.empty());
        when(ecosystemService.toResponse(any(), any())).thenReturn(
                new com.plateforme.ecosystem.dto.NicheRequestResponse(
                        requestId, "MCT-X", "T", "D", "FR", 2, java.util.List.of("TIKTOK"),
                        null, null, null, null, 1000, "10,00 €", "REJECTED", "UNPAID",
                        true, null, null, null, null, null, "REJECTED", null, "raison", null, "e", "n"
                ));

        validationService.validateModel(requestId, clientId, new ValidateModelDto(false, "pas convaincu"));

        assertThat(nr.getStatus()).isEqualTo(NicheStatus.REJECTED);
        assertThat(nr.getDeletedAt()).isNotNull();
        verify(notificationService).createAndSend(eq(agent.getId()), eq("DEMO_REJECTED"), anyString(), anyString(),
                eq("PLATFORM"), eq(requestId));
    }

    @Test
    @DisplayName("skipModelValidation PENDING bot confirmé → VALIDATED")
    void skipModelValidation_fromPending() {
        NicheRequest nr = new NicheRequest();
        nr.setClient(client);
        nr.setStatus(NicheStatus.PENDING);
        nr.setBotConfirmed(true);
        when(nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)).thenReturn(Optional.of(nr));
        when(scheduledConfigRepository.findByNicheRequest_Id(any())).thenReturn(Optional.empty());
        when(ecosystemService.toResponse(any(), any())).thenReturn(
                new com.plateforme.ecosystem.dto.NicheRequestResponse(
                        requestId, "MCT-X", "T", "D", "FR", 2, java.util.List.of("TIKTOK"),
                        null, null, null, null, 1000, "10,00 €", "VALIDATED", "UNPAID",
                        true, null, null, null, null, null, "PAYMENT", null, null, null, "e", "n"
                ));

        validationService.skipModelValidation(requestId, clientId);

        assertThat(nr.getStatus()).isEqualTo(NicheStatus.VALIDATED);
        assertThat(nr.getValidatedAt()).isNotNull();
        verify(paymentService, never()).createCheckoutSession(any(), any());
    }

    @Test
    @DisplayName("validateModel mauvais statut → erreur")
    void validateModel_wrongStatus() {
        NicheRequest nr = new NicheRequest();
        nr.setClient(client);
        nr.setStatus(NicheStatus.PENDING);
        when(nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)).thenReturn(Optional.of(nr));

        assertThatThrownBy(() -> validationService.validateModel(requestId, clientId, new ValidateModelDto(true, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "MODEL_NOT_AVAILABLE");
    }
}
