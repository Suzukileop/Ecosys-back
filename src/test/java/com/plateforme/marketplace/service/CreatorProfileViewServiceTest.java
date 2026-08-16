package com.plateforme.marketplace.service;

import com.plateforme.shared.service.NotificationService;
import com.plateforme.user.entity.CreatorProfile;
import com.plateforme.user.entity.CreatorProfileVisit;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.CreatorProfileRepository;
import com.plateforme.user.repository.CreatorProfileVisitRepository;
import com.plateforme.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatorProfileViewServiceTest {

    @Mock CreatorProfileRepository creatorProfileRepository;
    @Mock CreatorProfileVisitRepository visitRepository;
    @Mock UserRepository userRepository;
    @Mock NotificationService notificationService;

    CreatorProfileViewService service;

    UUID creatorId;
    UUID viewerId;
    CreatorProfile profile;
    User creator;

    @BeforeEach
    void setUp() {
        service = new CreatorProfileViewService(
                creatorProfileRepository, visitRepository, userRepository, notificationService);
        creatorId = UUID.randomUUID();
        viewerId = UUID.randomUUID();
        creator = new User();
        creator.setId(creatorId);
        creator.setFullName("Creator");
        profile = new CreatorProfile();
        profile.setUser(creator);
        profile.setProfileVisits(6);
        when(creatorProfileRepository.findByUserIdAndUserDeletedAtIsNull(creatorId))
                .thenReturn(Optional.of(profile));
    }

    @Test
    @DisplayName("first visit creates row, syncs unique count, and notifies")
    void firstVisit_recordsAndNotifies() {
        when(visitRepository.findByCreatorUserIdAndVisitorKey(creatorId, "user:" + viewerId))
                .thenReturn(Optional.empty());
        when(visitRepository.countByCreatorUserId(creatorId)).thenReturn(7L);
        stubViewer("Amertio");

        var response = service.recordView(creatorId, viewerId, null);

        assertThat(response.recorded()).isTrue();
        assertThat(response.profileVisits()).isEqualTo(7L);
        verify(visitRepository).save(any(CreatorProfileVisit.class));
        verify(notificationService).createAndSend(
                eq(creatorId),
                eq("CREATOR_PROFILE_VISIT"),
                eq("Profile visit"),
                contains("Amertio"),
                eq("PLATFORM"),
                eq(creatorId),
                eq(viewerId));
        assertThat(profile.getProfileVisits()).isEqualTo(7);
        verify(creatorProfileRepository).save(profile);
    }

    @Test
    @DisplayName("revisit keeps unique count, increments visitCount, and notifies again")
    void revisit_notifiesWithoutIncrementingUniqueCount() {
        CreatorProfileVisit existing = existingVisit(LocalDateTime.now().minusHours(1));
        existing.setVisitCount(3);
        when(visitRepository.findByCreatorUserIdAndVisitorKey(creatorId, "user:" + viewerId))
                .thenReturn(Optional.of(existing));
        when(visitRepository.countByCreatorUserId(creatorId)).thenReturn(2L);
        stubViewer("Amertio");

        var response = service.recordView(creatorId, viewerId, null);

        assertThat(response.recorded()).isFalse();
        assertThat(response.profileVisits()).isEqualTo(2L);
        ArgumentCaptor<CreatorProfileVisit> captor = ArgumentCaptor.forClass(CreatorProfileVisit.class);
        verify(visitRepository).save(captor.capture());
        assertThat(captor.getValue().getVisitCount()).isEqualTo(4);
        assertThat(captor.getValue().getViewedAt()).isAfter(LocalDateTime.now().minusMinutes(1));
        verify(notificationService).createAndSend(
                eq(creatorId),
                eq("CREATOR_PROFILE_VISIT"),
                eq("Profile visit"),
                contains("Amertio"),
                eq("PLATFORM"),
                eq(creatorId),
                eq(viewerId));
    }

    @Test
    @DisplayName("creator viewing own profile is ignored")
    void selfView_ignored() {
        var response = service.recordView(creatorId, creatorId, null);

        assertThat(response.recorded()).isFalse();
        verify(visitRepository, never()).findByCreatorUserIdAndVisitorKey(any(), any());
        verify(notificationService, never()).createAndSend(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("anonymous first visit records without notification")
    void anonymousFirstVisit_noNotification() {
        when(visitRepository.findByCreatorUserIdAndVisitorKey(creatorId, "anon:guest-1"))
                .thenReturn(Optional.empty());
        when(visitRepository.countByCreatorUserId(creatorId)).thenReturn(1L);

        var response = service.recordView(creatorId, null, "guest-1");

        assertThat(response.recorded()).isTrue();
        verify(visitRepository).save(any(CreatorProfileVisit.class));
        verify(notificationService, never()).createAndSend(any(), any(), any(), any(), any(), any(), any());
    }

    private void stubViewer(String name) {
        User viewer = new User();
        viewer.setId(viewerId);
        viewer.setFullName(name);
        when(userRepository.findByIdAndDeletedAtIsNull(viewerId)).thenReturn(Optional.of(viewer));
    }

    private CreatorProfileVisit existingVisit(LocalDateTime viewedAt) {
        CreatorProfileVisit visit = new CreatorProfileVisit();
        visit.setCreatorUserId(creatorId);
        visit.setViewerUserId(viewerId);
        visit.setVisitorKey("user:" + viewerId);
        visit.setViewedAt(viewedAt);
        visit.setVisitCount(1);
        return visit;
    }
}
