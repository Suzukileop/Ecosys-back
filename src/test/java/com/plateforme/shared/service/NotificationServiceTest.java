package com.plateforme.shared.service;

import com.plateforme.ecosystem.storage.PublicMediaUrlResolver;
import com.plateforme.shared.dto.NotificationDto;
import com.plateforme.shared.entity.Notification;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.shared.mail.MailDeliveryService;
import com.plateforme.shared.repository.NotificationRepository;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.CreatorProfileRepository;
import com.plateforme.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CreatorProfileRepository creatorProfileRepository;

    @Mock
    private PublicMediaUrlResolver publicMediaUrlResolver;

    @Mock
    private MailDeliveryService mailDeliveryService;

    @InjectMocks
    private NotificationService notificationService;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        user.setEmail("user@test.com");
        user.setFullName("Test User");
        user.setPasswordHash("hashed");
    }

    @Test
    @DisplayName("createAndSend - PLATFORM : la notification est sauvegardée en base")
    void createAndSend_platform_savedToDatabase() {
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));

        Notification saved = new Notification();
        saved.setUser(user);
        saved.setType("TEST");
        saved.setTitle("Titre test");
        saved.setChannel(Notification.Channel.PLATFORM);
        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);

        notificationService.createAndSend(userId, "TEST", "Titre test",
                "Message test", "PLATFORM", null);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification captured = captor.getValue();
        assertThat(captured.getUser()).isEqualTo(user);
        assertThat(captured.getType()).isEqualTo("TEST");
        assertThat(captured.getTitle()).isEqualTo("Titre test");
        assertThat(captured.getChannel()).isEqualTo(Notification.Channel.PLATFORM);
        assertThat(captured.getIsRead()).isFalse();

        verify(mailDeliveryService, never()).sendPlainText(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("createAndSend - BOTH : envoi email déclenché après sauvegarde plateforme")
    void createAndSend_both_triggersEmail() {
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.createAndSend(userId, "T", "Titre both", "Corps both", "BOTH", null);

        verify(mailDeliveryService).sendPlainText("user@test.com", "Titre both", "Corps both");
    }

    @Test
    @DisplayName("createAndSend - EMAIL : notification EMAIL + envoi email")
    void createAndSend_email_triggersEmail() {
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.createAndSend(userId, "T", "Sujet mail", "Texte", "EMAIL", null);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getChannel()).isEqualTo(Notification.Channel.EMAIL);
        verify(mailDeliveryService).sendPlainText("user@test.com", "Sujet mail", "Texte");
    }

    @Test
    @DisplayName("createAndSend - Utilisateur introuvable → BusinessException")
    void createAndSend_userNotFound_throwsBusinessException() {
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                notificationService.createAndSend(userId, "TEST", "Titre", "Message", "PLATFORM", null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("USER_NOT_FOUND");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("createAndSend - skips duplicate actor notification within 5 minutes")
    void createAndSend_actorCooldown_skipsDuplicate() {
        UUID actorId = UUID.randomUUID();
        when(notificationRepository.existsByUser_IdAndTypeAndRefSecondaryIdAndCreatedAtAfter(
                eq(userId), eq("CREATOR_PROFILE_VISIT"), eq(actorId), any(LocalDateTime.class)))
                .thenReturn(true);

        notificationService.createAndSend(
                userId,
                "CREATOR_PROFILE_VISIT",
                "Profile visit",
                "Someone visited your profile.",
                "PLATFORM",
                userId,
                actorId);

        verify(userRepository, never()).findByIdAndDeletedAtIsNull(any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("createAndSend - allows actor notification after cooldown window")
    void createAndSend_actorCooldown_allowsWhenExpired() {
        UUID actorId = UUID.randomUUID();
        when(notificationRepository.existsByUser_IdAndTypeAndRefSecondaryIdAndCreatedAtAfter(
                eq(userId), eq("CREATOR_PROFILE_VISIT"), eq(actorId), any(LocalDateTime.class)))
                .thenReturn(false);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.createAndSend(
                userId,
                "CREATOR_PROFILE_VISIT",
                "Profile visit",
                "Someone visited your profile.",
                "PLATFORM",
                userId,
                actorId);

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("markAllAsRead - marque toutes les notifications non lues comme lues")
    void markAllAsRead_marksAllUnreadAsRead() {
        Notification n1 = new Notification();
        n1.setIsRead(false);
        n1.setUser(user);
        n1.setType("TYPE1");
        n1.setTitle("Notif 1");

        Notification n2 = new Notification();
        n2.setIsRead(false);
        n2.setUser(user);
        n2.setType("TYPE2");
        n2.setTitle("Notif 2");

        Notification n3 = new Notification();
        n3.setIsRead(true);
        n3.setUser(user);
        n3.setType("TYPE3");
        n3.setTitle("Notif 3 déjà lue");

        when(notificationRepository.findByUserId(userId)).thenReturn(List.of(n1, n2, n3));

        notificationService.markAllAsRead(userId);

        assertThat(n1.getIsRead()).isTrue();
        assertThat(n2.getIsRead()).isTrue();
        assertThat(n3.getIsRead()).isTrue();

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    @DisplayName("markAsRead - Notification d'un autre utilisateur → BusinessException")
    void markAsRead_wrongUser_throwsBusinessException() {
        UUID otherUserId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();

        User otherUser = new User();
        otherUser.setId(otherUserId);
        otherUser.setEmail("other@test.com");
        otherUser.setPasswordHash("hashed");

        Notification notif = new Notification();
        notif.setUser(otherUser);
        notif.setIsRead(false);

        when(notificationRepository.findById(notifId)).thenReturn(Optional.of(notif));

        assertThatThrownBy(() -> notificationService.markAsRead(notifId, userId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("NOTIFICATION_ACCESS_DENIED");
    }

    @Test
    @DisplayName("countUnread - retourne le bon compteur")
    void countUnread_returnsCorrectCount() {
        when(notificationRepository.countByUserIdAndIsReadFalse(userId)).thenReturn(3L);

        long count = notificationService.countUnread(userId);

        assertThat(count).isEqualTo(3L);
    }
}
