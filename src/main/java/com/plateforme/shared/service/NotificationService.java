package com.plateforme.shared.service;

import com.plateforme.shared.dto.NotificationDto;
import com.plateforme.shared.entity.Notification;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.shared.mail.MailDeliveryService;
import com.plateforme.shared.repository.NotificationRepository;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final MailDeliveryService mailDeliveryService;

    @Transactional
    public void createAndSend(UUID userId, String type, String title, String message,
                              String channelStr, UUID refId) {
        createAndSend(userId, type, title, message, channelStr, refId, null);
    }

    @Transactional
    public void createAndSend(UUID userId, String type, String title, String message,
                              String channelStr, UUID refId, UUID refSecondaryId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND",
                        "Utilisateur introuvable : " + userId));

        String normalized = channelStr != null ? channelStr.trim().toUpperCase() : "PLATFORM";

        if ("BOTH".equals(normalized)) {
            Notification notification = buildNotification(user, type, title, message, refId, refSecondaryId,
                    Notification.Channel.PLATFORM);
            notificationRepository.save(notification);
            sendEmailNonBlocking(user, title, message);
            log.debug("Notification BOTH pour user={} type={} refSecondary={}", userId, type, refSecondaryId);
            return;
        }

        Notification.Channel channel = switch (normalized) {
            case "EMAIL" -> Notification.Channel.EMAIL;
            default -> Notification.Channel.PLATFORM;
        };

        Notification notification = buildNotification(user, type, title, message, refId, refSecondaryId, channel);
        notificationRepository.save(notification);

        if ("EMAIL".equals(normalized)) {
            sendEmailNonBlocking(user, title, message);
        }

        log.debug("Notification créée pour user={} type={} channel={} refSecondary={}",
                userId, type, channel, refSecondaryId);
    }

    /**
     * Ne doit pas faire échouer la transaction métier si l’envoi échoue (implémentations synchrones).
     */
    private void sendEmailNonBlocking(User user, String title, String message) {
        try {
            mailDeliveryService.sendPlainText(
                    user.getEmail(),
                    title != null ? title : "Notification",
                    message != null ? message : "");
        } catch (Exception e) {
            log.warn("Envoi email ignoré pour user={} : {}", user.getId(), e.getMessage());
        }
    }

    private Notification buildNotification(User user, String type, String title, String message,
                                           UUID refId, UUID refSecondaryId, Notification.Channel channel) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setChannel(channel);
        notification.setRefId(refId);
        notification.setRefSecondaryId(refSecondaryId);
        return notification;
    }

    @Transactional
    public void sendBulkToRole(String roleName, String type, String title, String message) {
        sendBulkToRole(roleName, type, title, message, null);
    }

    @Transactional
    public void sendBulkToRole(String roleName, String type, String title, String message, UUID refId) {
        List<User> users = userRepository.findAllByDeletedAtIsNull(Pageable.unpaged()).getContent()
                .stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> r.getName().equals(roleName)))
                .toList();

        log.info("Envoi notification bulk role={} refId={} : {} destinataires", roleName, refId, users.size());

        for (User user : users) {
            Notification notification = buildNotification(user, type, title, message, refId, null,
                    Notification.Channel.PLATFORM);
            notificationRepository.save(notification);
        }
    }

    /**
     * Notifie l'agent assigné s'il existe, sinon tous les agents du rôle.
     */
    @Transactional
    public void notifyAgentOrAll(UUID assignedAgentId, String type, String title, String message, UUID refId) {
        if (assignedAgentId != null) {
            createAndSend(assignedAgentId, type, title, message, "PLATFORM", refId);
            return;
        }
        sendBulkToRole("ROLE_AGENT", type, title, message, refId);
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto> getMyNotifications(UUID userId, Pageable pageable) {
        return notificationRepository
                .findByUserIdOrderByIsReadAscCreatedAtDesc(userId, pageable)
                .map(this::toDto);
    }

    @Transactional
    public void markAsRead(UUID notifId, UUID userId) {
        Notification notification = notificationRepository.findById(notifId)
                .orElseThrow(() -> new BusinessException("NOTIFICATION_NOT_FOUND",
                        "Notification introuvable : " + notifId));

        UUID ownerId = notification.getUser() != null ? notification.getUser().getId() : null;
        if (!Objects.equals(ownerId, userId)) {
            throw new BusinessException("NOTIFICATION_ACCESS_DENIED",
                    "Cette notification n'appartient pas à l'utilisateur courant");
        }

        notification.setIsRead(true);
        notificationRepository.save(notification);
        log.debug("Notification {} marquée comme lue par user={}", notifId, userId);
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        List<Notification> notifications = notificationRepository.findByUserId(userId)
                .stream()
                .filter(n -> !Boolean.TRUE.equals(n.getIsRead()))
                .toList();

        notifications.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(notifications);
        log.debug("{} notification(s) marquées comme lues pour user={}", notifications.size(), userId);
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    private NotificationDto toDto(Notification n) {
        return new NotificationDto(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getMessage(),
                n.getIsRead(),
                n.getChannel().name(),
                n.getRefId(),
                n.getRefSecondaryId(),
                n.getCreatedAt()
        );
    }
}
