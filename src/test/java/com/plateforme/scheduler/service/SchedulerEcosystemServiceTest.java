package com.plateforme.scheduler.service;

import com.plateforme.ecosystem.entity.NicheRequest;
import com.plateforme.ecosystem.entity.NicheStatus;
import com.plateforme.ecosystem.repository.NicheRequestRepository;
import com.plateforme.scheduler.dto.PublicationSlotDto;
import com.plateforme.scheduler.dto.ScheduledConfigDto;
import com.plateforme.scheduler.dto.ScheduledPostRequest;
import com.plateforme.scheduler.entity.ContentType;
import com.plateforme.scheduler.entity.Platform;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulerEcosystemServiceTest {

    @Mock
    private ScheduledPostRepository scheduledPostRepository;

    @Mock
    private PublicationAnalyticsRepository publicationAnalyticsRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private NicheRequestRepository nicheRequestRepository;

    @Mock
    private ScheduledConfigRepository scheduledConfigRepository;

    @InjectMocks
    private SchedulerEcosystemService schedulerEcosystemService;

    private UUID clientId;
    private User client;
    private UUID nicheId;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        nicheId = UUID.randomUUID();
        client = new User();
        client.setId(clientId);
        client.setEmail("c@test.com");
        client.setPasswordHash("x");
    }

    @Test
    @DisplayName("scheduleManualPost : succès avec niche ACTIVE")
    void scheduleManualPost_success() {
        LocalDateTime scheduledAt = LocalDateTime.now().plusHours(1);

        when(userRepository.findByIdAndDeletedAtIsNull(clientId)).thenReturn(Optional.of(client));

        NicheRequest nr = new NicheRequest();
        nr.setId(nicheId);
        nr.setClient(client);
        nr.setStatus(NicheStatus.ACTIVE);
        when(nicheRequestRepository.findByIdAndClient_Id(nicheId, clientId)).thenReturn(Optional.of(nr));

        ScheduledConfig cfg = new ScheduledConfig();
        cfg.setNicheRequest(nr);
        cfg.setClient(client);
        cfg.setPublicationSlots(List.of(
                new PublicationSlotDto(1, "09:00"),
                new PublicationSlotDto(3, "09:00"),
                new PublicationSlotDto(5, "09:00")));
        when(scheduledConfigRepository.findByNicheRequest_IdAndClient_Id(nicheId, clientId))
                .thenReturn(Optional.of(cfg));

        when(scheduledPostRepository.save(any(ScheduledPost.class))).thenAnswer(invocation -> {
            ScheduledPost p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });

        ScheduledPostRequest req = new ScheduledPostRequest(
                nicheId,
                Platform.TIKTOK,
                "https://example.com/v",
                ContentType.EXTERNAL_URL,
                "caption",
                scheduledAt,
                null
        );

        var resp = schedulerEcosystemService.scheduleManualPost(clientId, req);

        assertThat(resp.status()).isEqualTo(PostStatus.SCHEDULED);
        verify(scheduledPostRepository).save(any(ScheduledPost.class));
    }

    @Test
    @DisplayName("scheduleManualPost : trop tôt → BusinessException")
    void scheduleManualPost_tooSoon_throwsBusinessException() {
        LocalDateTime scheduledAt = LocalDateTime.now().plusMinutes(1);
        when(userRepository.findByIdAndDeletedAtIsNull(clientId)).thenReturn(Optional.of(client));

        NicheRequest nr = new NicheRequest();
        nr.setId(nicheId);
        nr.setStatus(NicheStatus.ACTIVE);
        when(nicheRequestRepository.findByIdAndClient_Id(nicheId, clientId)).thenReturn(Optional.of(nr));
        ScheduledConfig cfg = new ScheduledConfig();
        cfg.setNicheRequest(nr);
        cfg.setClient(client);
        when(scheduledConfigRepository.findByNicheRequest_IdAndClient_Id(nicheId, clientId))
                .thenReturn(Optional.of(cfg));

        ScheduledPostRequest req = new ScheduledPostRequest(
                nicheId,
                Platform.TIKTOK,
                "https://example.com/v",
                ContentType.EXTERNAL_URL,
                null,
                scheduledAt,
                null
        );

        assertThatThrownBy(() -> schedulerEcosystemService.scheduleManualPost(clientId, req))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("SCHEDULE_TOO_SOON");

        verify(scheduledPostRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelPost : succès → CANCELLED")
    void cancelPost_success() {
        UUID postId = UUID.randomUUID();
        ScheduledPost post = new ScheduledPost();
        post.setId(postId);
        post.setClient(client);
        post.setStatus(PostStatus.SCHEDULED);

        when(scheduledPostRepository.findById(postId)).thenReturn(Optional.of(post));

        schedulerEcosystemService.cancelPost(clientId, postId);

        assertThat(post.getStatus()).isEqualTo(PostStatus.CANCELLED);
        verify(scheduledPostRepository).save(post);
    }

    @Test
    @DisplayName("cancelPost : mauvais propriétaire → AccessDeniedException")
    void cancelPost_wrongOwner_throwsAccessDenied() {
        UUID postId = UUID.randomUUID();
        User other = new User();
        other.setId(UUID.randomUUID());

        ScheduledPost post = new ScheduledPost();
        post.setId(postId);
        post.setClient(other);
        post.setStatus(PostStatus.SCHEDULED);

        when(scheduledPostRepository.findById(postId)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> schedulerEcosystemService.cancelPost(clientId, postId))
                .isInstanceOf(AccessDeniedException.class);

        verify(scheduledPostRepository, never()).save(any());
    }

    @Test
    @DisplayName("processPost : crée une ligne analytics")
    void processPost_createsAnalytics() {
        UUID postId = UUID.randomUUID();
        ScheduledPost post = new ScheduledPost();
        post.setId(postId);
        post.setClient(client);
        post.setPlatform(Platform.INSTAGRAM);
        post.setStatus(PostStatus.SCHEDULED);

        when(scheduledPostRepository.findById(postId)).thenReturn(Optional.of(post));
        when(scheduledPostRepository.save(any(ScheduledPost.class))).thenAnswer(inv -> inv.getArgument(0));

        schedulerEcosystemService.processPost(postId);

        verify(publicationAnalyticsRepository).save(any(PublicationAnalytics.class));
    }

    @Test
    @DisplayName("processPost : notification BOTH")
    void processPost_sendsNotification() {
        UUID postId = UUID.randomUUID();
        ScheduledPost post = new ScheduledPost();
        post.setId(postId);
        post.setClient(client);
        post.setPlatform(Platform.YOUTUBE);
        post.setStatus(PostStatus.SCHEDULED);

        when(scheduledPostRepository.findById(postId)).thenReturn(Optional.of(post));
        when(scheduledPostRepository.save(any(ScheduledPost.class))).thenAnswer(inv -> inv.getArgument(0));

        schedulerEcosystemService.processPost(postId);

        verify(notificationService).createAndSend(
                eq(clientId),
                eq("POST_PUBLISHED"),
                anyString(),
                anyString(),
                eq("BOTH"),
                eq(postId)
        );
    }

    @Test
    @DisplayName("getMyPosts : status invalide → BusinessException")
    void getMyPosts_invalidStatus_throws() {
        assertThatThrownBy(() -> schedulerEcosystemService.getMyPosts(clientId, "NOT_A_STATUS", PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_POST_STATUS");
    }

    @Test
    @DisplayName("processScheduledPosts : traite les posts dus")
    void processScheduledPosts_runsBatch() {
        ScheduledPost p = new ScheduledPost();
        p.setId(UUID.randomUUID());
        p.setClient(client);
        p.setPlatform(Platform.TIKTOK);
        p.setStatus(PostStatus.SCHEDULED);

        when(scheduledPostRepository.findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                eq(PostStatus.SCHEDULED), any(LocalDateTime.class)))
                .thenReturn(List.of(p));

        when(scheduledPostRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(scheduledPostRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        schedulerEcosystemService.processScheduledPosts();

        verify(publicationAnalyticsRepository).save(any(PublicationAnalytics.class));
    }

    @Test
    @DisplayName("updateScheduledConfig : plusieurs créneaux le même jour (heures différentes)")
    void updateScheduledConfig_sameDayMultipleTimes_ok() {
        UUID requestId = UUID.randomUUID();
        NicheRequest nr = new NicheRequest();
        nr.setId(requestId);
        nr.setClient(client);
        nr.setStatus(NicheStatus.ACTIVE);
        nr.setNbPostsPerWeek((short) 2);
        when(nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)).thenReturn(Optional.of(nr));

        ScheduledConfig cfg = new ScheduledConfig();
        cfg.setNicheRequest(nr);
        cfg.setClient(client);
        cfg.setPublicationSlots(List.of(new PublicationSlotDto(1, "09:00"), new PublicationSlotDto(2, "09:00")));
        when(scheduledConfigRepository.findByNicheRequest_IdAndClient_Id(requestId, clientId))
                .thenReturn(Optional.of(cfg));
        when(scheduledConfigRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ScheduledConfigDto dto = new ScheduledConfigDto(requestId, List.of(
                new PublicationSlotDto(1, "08:00"),
                new PublicationSlotDto(1, "21:00")
        ));

        var out = schedulerEcosystemService.updateScheduledConfig(clientId, requestId, dto);

        assertThat(out.publicationSlots()).hasSize(2);
        assertThat(out.publicationSlots().get(0).time()).isEqualTo("08:00");
        assertThat(out.publicationSlots().get(1).time()).isEqualTo("21:00");
        verify(scheduledConfigRepository).save(cfg);
    }

    @Test
    @DisplayName("updateScheduledConfig : nombre de créneaux ≠ nbPostsPerWeek → erreur")
    void updateScheduledConfig_slotCountMismatch_throws() {
        UUID requestId = UUID.randomUUID();
        NicheRequest nr = new NicheRequest();
        nr.setId(requestId);
        nr.setClient(client);
        nr.setStatus(NicheStatus.ACTIVE);
        nr.setNbPostsPerWeek((short) 12);
        when(nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)).thenReturn(Optional.of(nr));

        ScheduledConfig cfg = new ScheduledConfig();
        cfg.setNicheRequest(nr);
        cfg.setClient(client);
        when(scheduledConfigRepository.findByNicheRequest_IdAndClient_Id(requestId, clientId))
                .thenReturn(Optional.of(cfg));

        ScheduledConfigDto dto = new ScheduledConfigDto(requestId, List.of(new PublicationSlotDto(1, "08:00")));

        assertThatThrownBy(() -> schedulerEcosystemService.updateScheduledConfig(clientId, requestId, dto))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("SLOT_COUNT_MISMATCH");

        verify(scheduledConfigRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateScheduledConfig : créneau (jour+heure) dupliqué → erreur")
    void updateScheduledConfig_duplicateSlot_throws() {
        UUID requestId = UUID.randomUUID();
        NicheRequest nr = new NicheRequest();
        nr.setId(requestId);
        nr.setClient(client);
        nr.setStatus(NicheStatus.ACTIVE);
        nr.setNbPostsPerWeek((short) 2);
        when(nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)).thenReturn(Optional.of(nr));

        ScheduledConfig cfg = new ScheduledConfig();
        cfg.setNicheRequest(nr);
        cfg.setClient(client);
        when(scheduledConfigRepository.findByNicheRequest_IdAndClient_Id(requestId, clientId))
                .thenReturn(Optional.of(cfg));

        ScheduledConfigDto dto = new ScheduledConfigDto(requestId, List.of(
                new PublicationSlotDto(3, "14:00"),
                new PublicationSlotDto(3, "14:00")
        ));

        assertThatThrownBy(() -> schedulerEcosystemService.updateScheduledConfig(clientId, requestId, dto))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("DUPLICATE_PUBLICATION_SLOT");

        verify(scheduledConfigRepository, never()).save(any());
    }

    @Test
    @DisplayName("getPostsByNicheRequest : liste les posts liés à une niche")
    void getPostsByNicheRequest_returnsPosts() {
        UUID requestId = UUID.randomUUID();
        NicheRequest nr = new NicheRequest();
        nr.setId(requestId);
        nr.setClient(client);

        when(nicheRequestRepository.findByIdAndClient_Id(requestId, clientId)).thenReturn(Optional.of(nr));

        ScheduledPost post = new ScheduledPost();
        post.setId(UUID.randomUUID());
        post.setClient(client);
        post.setNicheRequest(nr);
        post.setPlatform(Platform.TIKTOK);
        post.setContentUrl("https://cdn.example.com/video.mp4");
        post.setContentType(ContentType.EXTERNAL_URL);
        post.setCaption("Test");
        post.setScheduledAt(LocalDateTime.now().plusDays(1));
        post.setStatus(PostStatus.SCHEDULED);
        post.setCreatedAt(LocalDateTime.now());

        when(scheduledPostRepository.findByNicheRequest_IdAndClient_Id(
                eq(requestId), eq(clientId), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(post)));

        var page = schedulerEcosystemService.getPostsByNicheRequest(
                clientId, requestId, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).platform()).isEqualTo(Platform.TIKTOK);
        assertThat(page.getContent().get(0).contentUrl()).isEqualTo("https://cdn.example.com/video.mp4");
    }
}
