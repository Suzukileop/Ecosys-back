package com.plateforme.shared.service;

import com.plateforme.ecosystem.storage.PublicMediaUrlResolver;
import com.plateforme.shared.dto.NotificationDto;
import com.plateforme.shared.entity.Notification;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.shared.mail.MailDeliveryService;
import com.plateforme.shared.repository.NotificationRepository;
import com.plateforme.user.entity.CreatorProfile;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.CreatorProfileRepository;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final CreatorProfileRepository creatorProfileRepository;
    private final PublicMediaUrlResolver publicMediaUrlResolver;
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
        List<User> users = userRepository.findByRoleNameAndDeletedAtIsNull(roleName);
        for (User u : users) {
            Notification notification = buildNotification(u, type, title, message, refId, null,
                    Notification.Channel.PLATFORM);
            notificationRepository.save(notification);
        }
        log.debug("Bulk notification type={} role={} recipients={}", type, roleName, users.size());
    }

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
        Page<Notification> page = notificationRepository
                .findByUserIdOrderByIsReadAscCreatedAtDesc(userId, pageable);
        ActorLookup actors = loadActors(page.getContent());
        List<NotificationDto> content = page.getContent().stream()
                .map(n -> toDto(n, actors))
                .toList();
        return new PageImpl<>(content, pageable, page.getTotalElements());
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

    private ActorLookup loadActors(List<Notification> notifications) {
        List<UUID> actorIds = notifications.stream()
                .map(Notification::getRefSecondaryId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (actorIds.isEmpty()) {
            return ActorLookup.empty();
        }
        Map<UUID, User> usersById = userRepository.findByIdInAndDeletedAtIsNull(actorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Set<UUID> creatorIds = creatorProfileRepository.findByUser_IdIn(actorIds).stream()
                .map(cp -> cp.getUser() != null ? cp.getUser().getId() : null)
                .filter(Objects::nonNull)
                .filter(usersById::containsKey)
                .collect(Collectors.toCollection(HashSet::new));
        return new ActorLookup(usersById, creatorIds);
    }

    private NotificationDto toDto(Notification n, ActorLookup actors) {
        UUID actorId = n.getRefSecondaryId();
        User actor = actorId != null ? actors.usersById().get(actorId) : null;
        String actorName = null;
        String actorAvatar = null;
        Boolean profileAvailable = null;
        if (actorId != null) {
            profileAvailable = actors.creatorUserIds().contains(actorId);
            if (actor != null) {
                if (actor.getFullName() != null && !actor.getFullName().isBlank()) {
                    actorName = actor.getFullName().trim();
                }
                actorAvatar = publicMediaUrlResolver.resolveAvatarUrl(actor.getAvatarUrl());
            }
        }
        return new NotificationDto(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getMessage(),
                n.getIsRead(),
                n.getChannel().name(),
                n.getRefId(),
                n.getRefSecondaryId(),
                n.getCreatedAt(),
                actorName,
                actorAvatar,
                profileAvailable
        );
    }

    private record ActorLookup(Map<UUID, User> usersById, Set<UUID> creatorUserIds) {
        static ActorLookup empty() {
            return new ActorLookup(Map.of(), Set.of());
        }
    }
}
