package com.plateforme.ecosystem.service;

import com.plateforme.ecosystem.dto.NicheRequestFormDto;
import com.plateforme.ecosystem.entity.NicheRequest;
import com.plateforme.ecosystem.entity.NicheStatus;
import com.plateforme.ecosystem.repository.NicheRequestRepository;
import com.plateforme.ecosystem.storage.StorageService;
import com.plateforme.scheduler.repository.ScheduledConfigRepository;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.shared.service.NotificationService;
import com.plateforme.shared.service.PlatformConfigService;
import com.plateforme.shared.util.UniqueCodeGenerator;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EcosystemServiceTest {

    @Mock
    private NicheRequestRepository nicheRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UniqueCodeGenerator uniqueCodeGenerator;

    @Mock
    private PlatformConfigService platformConfigService;

    @Mock
    private StorageService storageService;

    @Mock
    private ScheduledConfigRepository scheduledConfigRepository;

    @InjectMocks
    private EcosystemService ecosystemService;

    private User client;
    private UUID clientId;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        client = new User();
        client.setId(clientId);
        client.setEmail("c@test.com");
        client.setPasswordHash("h");
    }

    @Test
    @DisplayName("submitNicheRequest : MCT, pas de notif agents avant confirmation bot")
    void submitNicheRequest_success() {
        NicheRequestFormDto dto = new NicheRequestFormDto(
                "Thème", "Description longue", "FR", 3,
                List.of("INSTAGRAM"), "MCT", "MCT-AB12", null
        );
        when(userRepository.findByIdAndDeletedAtIsNull(clientId)).thenReturn(Optional.of(client));
        when(uniqueCodeGenerator.generate()).thenReturn("MCT-XY00");
        when(platformConfigService.calculateMonthlyAmount(3)).thenReturn(12_000);
        when(nicheRequestRepository.save(any(NicheRequest.class))).thenAnswer(i -> i.getArgument(0));

        var res = ecosystemService.submitNicheRequest(clientId, dto);

        assertThat(res.uniqueCode()).isEqualTo("MCT-XY00");
        assertThat(res.status()).isEqualTo("PENDING");
        assertThat(res.monthlyAmountCents()).isEqualTo(12_000);
        verify(notificationService, never()).sendBulkToRole(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("submitNicheRequest : montant = nb * 4 * tarif")
    void submitNicheRequest_calculatesAmount() {
        NicheRequestFormDto dto = new NicheRequestFormDto(
                "T", "D", "FR", 2, List.of("TIKTOK"), null, null, null
        );
        when(userRepository.findByIdAndDeletedAtIsNull(clientId)).thenReturn(Optional.of(client));
        when(uniqueCodeGenerator.generate()).thenReturn("MCT-AA11");
        when(platformConfigService.calculateMonthlyAmount(2)).thenReturn(8000);
        when(nicheRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var res = ecosystemService.submitNicheRequest(clientId, dto);

        assertThat(res.monthlyAmountCents()).isEqualTo(8000);
        verify(platformConfigService).calculateMonthlyAmount(2);
    }

    @Test
    @DisplayName("cancelRequest : PENDING → CANCELLED + soft delete")
    void cancelRequest_fromPending() {
        UUID rid = UUID.randomUUID();
        NicheRequest nr = new NicheRequest();
        nr.setId(rid);
        nr.setClient(client);
        nr.setStatus(NicheStatus.PENDING);
        when(nicheRequestRepository.findByIdAndClient_Id(rid, clientId)).thenReturn(Optional.of(nr));
        when(nicheRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ecosystemService.cancelRequest(rid, clientId);

        assertThat(nr.getStatus()).isEqualTo(NicheStatus.CANCELLED);
        assertThat(nr.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("cancelRequest : ACTIVE → erreur")
    void cancelRequest_fromActive_throws() {
        UUID rid = UUID.randomUUID();
        NicheRequest nr = new NicheRequest();
        nr.setStatus(NicheStatus.ACTIVE);
        when(nicheRequestRepository.findByIdAndClient_Id(rid, clientId)).thenReturn(Optional.of(nr));

        assertThatThrownBy(() -> ecosystemService.cancelRequest(rid, clientId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "NICHE_REQUEST_CANNOT_CANCEL");
    }

    @Test
    @DisplayName("confirmBotChat : bot_confirmed + notifications agent et client")
    void confirmBotChat_setsFlag() {
        UUID rid = UUID.randomUUID();
        NicheRequest nr = new NicheRequest();
        nr.setId(rid);
        nr.setClient(client);
        nr.setNicheTheme("Fitness");
        nr.setStatus(NicheStatus.PENDING);
        nr.setBotConfirmed(false);
        when(nicheRequestRepository.findByIdAndClient_Id(rid, clientId)).thenReturn(Optional.of(nr));
        when(scheduledConfigRepository.findByNicheRequest_Id(rid)).thenReturn(Optional.empty());
        when(nicheRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var res = ecosystemService.confirmBotChat(rid, clientId);

        assertThat(res.botConfirmed()).isTrue();
        assertThat(nr.getBotConfirmedAt()).isNotNull();
        verify(notificationService).sendBulkToRole(
                eq("ROLE_AGENT"), eq("NICHE_WAITING_VALIDATION"), anyString(), anyString(), eq(rid));
        verify(notificationService).createAndSend(
                eq(clientId), eq("NICHE_PENDING_MODEL"), anyString(), anyString(), eq("PLATFORM"), eq(rid));
    }
}
