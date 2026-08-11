package com.plateforme.payment.service;

import com.plateforme.ecosystem.entity.NicheRequest;
import com.plateforme.ecosystem.entity.NicheStatus;
import com.plateforme.ecosystem.entity.PaymentStatus;
import com.plateforme.ecosystem.repository.NicheRequestRepository;
import com.plateforme.payment.client.VanillaPayClient;
import com.plateforme.scheduler.entity.ScheduledConfig;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private VanillaPayClient vanillaPayClient;

    @Mock
    private NicheRequestRepository nicheRequestRepository;

    @Mock
    private ScheduledConfigRepository scheduledConfigRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "frontendUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(paymentService, "backendPublicUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(paymentService, "currency", "MGA");
        ReflectionTestUtils.setField(paymentService, "paymentMethod", "mobile_money");
        ReflectionTestUtils.setField(paymentService, "eurToMgaRate", 4920L);
    }

    @Test
    @DisplayName("activateEcosystem crée ScheduledConfig par défaut")
    void activateEcosystem_createsDefaultConfig() {
        UUID rid = UUID.randomUUID();
        User client = new User();
        client.setId(UUID.randomUUID());
        client.setEmail("x@test.com");

        NicheRequest nr = new NicheRequest();
        nr.setId(rid);
        nr.setClient(client);
        nr.setNicheTheme("Thème");
        nr.setPlatforms(new ArrayList<>(List.of("INSTAGRAM")));

        when(nicheRequestRepository.findById(rid)).thenReturn(Optional.of(nr));
        when(scheduledConfigRepository.findByNicheRequest_Id(rid)).thenReturn(Optional.empty());
        when(scheduledConfigRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        paymentService.activateEcosystem(rid);

        assertThat(nr.getStatus()).isEqualTo(NicheStatus.ACTIVE);
        verify(scheduledConfigRepository).save(argThat(saved -> {
            ScheduledConfig c = (ScheduledConfig) saved;
            return c.getPublicationSlots() != null && c.getPublicationSlots().isEmpty();
        }));
        verify(notificationService).createAndSend(eq(client.getId()), eq("ECOSYSTEM_ACTIVE"), anyString(),
                anyString(), eq("BOTH"), eq(rid));
        verify(notificationService).notifyAgentOrAll(isNull(), eq("NICHE_ACTIVATED"), anyString(),
                anyString(), eq(rid));
    }

    @Test
    @DisplayName("createCheckoutSession : simulation sans VPI → payé, actif, URL succès simulée")
    void createCheckoutSession_simulation_paidAndActive() {
        ReflectionTestUtils.setField(paymentService, "simulateWithoutVpi", true);
        when(vanillaPayClient.isConfigured()).thenReturn(false);

        UUID requestId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        User client = new User();
        client.setId(clientId);
        client.setEmail("c@test.com");

        NicheRequest nr = new NicheRequest();
        nr.setId(requestId);
        nr.setClient(client);
        nr.setStatus(NicheStatus.VALIDATED);
        nr.setNicheTheme("Thème");
        nr.setMonthlyAmountCents(60_000);
        nr.setPlatforms(new ArrayList<>(List.of("TIKTOK")));

        when(nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)).thenReturn(Optional.of(nr));
        when(nicheRequestRepository.findById(requestId)).thenReturn(Optional.of(nr));
        when(scheduledConfigRepository.findByNicheRequest_Id(requestId)).thenReturn(Optional.empty());
        when(scheduledConfigRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(nicheRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        String url = paymentService.createCheckoutSession(requestId, clientId);

        assertThat(url).isEqualTo(
                "http://localhost:3000/dashboard/ecosystem/" + requestId + "?payment=success&simulated=true");
        assertThat(nr.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(nr.getStatus()).isEqualTo(NicheStatus.ACTIVE);
        assertThat(nr.getVpiReference()).startsWith("sim_ref_");
        verify(notificationService).createAndSend(eq(clientId), eq("ECOSYSTEM_ACTIVE"), anyString(),
                anyString(), eq("BOTH"), eq(requestId));
        verify(notificationService).notifyAgentOrAll(isNull(), eq("NICHE_ACTIVATED"), anyString(),
                anyString(), eq(requestId));
    }

    @Test
    @DisplayName("createCheckoutSession : sans VPI ni simulation → VPI_NOT_CONFIGURED")
    void createCheckoutSession_noVpi_throws() {
        ReflectionTestUtils.setField(paymentService, "simulateWithoutVpi", false);
        when(vanillaPayClient.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> paymentService.createCheckoutSession(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "VPI_NOT_CONFIGURED");

        verifyNoInteractions(nicheRequestRepository);
    }
}
