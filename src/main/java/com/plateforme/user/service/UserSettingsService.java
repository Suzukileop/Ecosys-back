package com.plateforme.user.service;

import com.plateforme.user.dto.UpdateUserSettingsDto;
import com.plateforme.user.dto.UserSettingsDto;
import com.plateforme.user.entity.UserSettings;
import com.plateforme.user.entity.UserSettings.MessagePermission;
import com.plateforme.user.repository.CreatorFollowRepository;
import com.plateforme.user.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Notification and privacy preferences. Rows are created on first write; reads fall back to defaults.
 * Checks are phrased negatively ("muted", "hides", "rejects") so a missing row — or an unstubbed
 * mock — keeps the permissive default.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserSettingsService {

    private static final Map<String, Function<UserSettings, Boolean>> NOTIFICATION_TOGGLES = Map.of(
            "CONVERSATION_GUEST_INVITE", UserSettings::getNotifyMessages,
            "MARKETPLACE_NEW_COMMENT", UserSettings::getNotifyComments,
            "CREATOR_NEW_FOLLOWER", UserSettings::getNotifyFollowers,
            "CREATOR_PROFILE_VISIT", UserSettings::getNotifyProfileVisits,
            "MARKETPLACE_SALE", UserSettings::getNotifySales,
            FollowerPublishNotifyService.TYPE_NEW_CONTENT, UserSettings::getNotifyFollowingActivity,
            FollowerPublishNotifyService.TYPE_NEW_PRODUCT, UserSettings::getNotifyFollowingActivity,
            FollowerPublishNotifyService.TYPE_NEW_SERVICE, UserSettings::getNotifyFollowingActivity
    );

    private final UserSettingsRepository userSettingsRepository;
    private final CreatorFollowRepository creatorFollowRepository;

    @Transactional(readOnly = true)
    public UserSettingsDto getSettings(UUID userId) {
        return toDto(load(userId));
    }

    @Transactional
    public UserSettingsDto updateSettings(UUID userId, UpdateUserSettingsDto dto) {
        UserSettings settings = userSettingsRepository.findById(userId).orElseGet(() -> new UserSettings(userId));
        if (dto.emailNotifications() != null) settings.setEmailNotifications(dto.emailNotifications());
        if (dto.notifyMessages() != null) settings.setNotifyMessages(dto.notifyMessages());
        if (dto.notifyComments() != null) settings.setNotifyComments(dto.notifyComments());
        if (dto.notifyFollowers() != null) settings.setNotifyFollowers(dto.notifyFollowers());
        if (dto.notifyProfileVisits() != null) settings.setNotifyProfileVisits(dto.notifyProfileVisits());
        if (dto.notifySales() != null) settings.setNotifySales(dto.notifySales());
        if (dto.notifyFollowingActivity() != null) settings.setNotifyFollowingActivity(dto.notifyFollowingActivity());
        if (dto.showOnlineStatus() != null) settings.setShowOnlineStatus(dto.showOnlineStatus());
        if (dto.privateProfileViews() != null) settings.setPrivateProfileViews(dto.privateProfileViews());
        if (dto.messagePermission() != null) {
            settings.setMessagePermission(MessagePermission.valueOf(dto.messagePermission()));
        }
        settings = userSettingsRepository.save(settings);
        log.info("User settings updated user={}", userId);
        return toDto(settings);
    }

    /** True when the user turned off in-app notifications of this type. Transactional types are never muted. */
    @Transactional(readOnly = true)
    public boolean isNotificationMuted(UUID userId, String type) {
        if (userId == null || type == null) return false;
        Function<UserSettings, Boolean> toggle = NOTIFICATION_TOGGLES.get(type);
        if (toggle == null) return false;
        return !Boolean.TRUE.equals(toggle.apply(load(userId)));
    }

    @Transactional(readOnly = true)
    public boolean isEmailMuted(UUID userId) {
        return userId != null && !Boolean.TRUE.equals(load(userId).getEmailNotifications());
    }

    @Transactional(readOnly = true)
    public boolean hidesOnlineStatus(UUID userId) {
        return userId != null && !Boolean.TRUE.equals(load(userId).getShowOnlineStatus());
    }

    @Transactional(readOnly = true)
    public boolean browsesPrivately(UUID userId) {
        return userId != null && Boolean.TRUE.equals(load(userId).getPrivateProfileViews());
    }

    /** Whether {@code recipientId} refuses a <em>new</em> direct conversation started by {@code senderId}. */
    @Transactional(readOnly = true)
    public boolean rejectsNewMessagesFrom(UUID recipientId, UUID senderId) {
        if (recipientId == null || senderId == null) return false;
        return switch (load(recipientId).getMessagePermission()) {
            case EVERYONE -> false;
            case NOBODY -> true;
            case FOLLOWING -> !creatorFollowRepository.existsByFollower_IdAndCreator_Id(recipientId, senderId);
        };
    }

    private UserSettings load(UUID userId) {
        return userSettingsRepository.findById(userId).orElseGet(() -> new UserSettings(userId));
    }

    private static UserSettingsDto toDto(UserSettings s) {
        return new UserSettingsDto(
                Boolean.TRUE.equals(s.getEmailNotifications()),
                Boolean.TRUE.equals(s.getNotifyMessages()),
                Boolean.TRUE.equals(s.getNotifyComments()),
                Boolean.TRUE.equals(s.getNotifyFollowers()),
                Boolean.TRUE.equals(s.getNotifyProfileVisits()),
                Boolean.TRUE.equals(s.getNotifySales()),
                Boolean.TRUE.equals(s.getNotifyFollowingActivity()),
                Boolean.TRUE.equals(s.getShowOnlineStatus()),
                Boolean.TRUE.equals(s.getPrivateProfileViews()),
                s.getMessagePermission() != null ? s.getMessagePermission().name() : MessagePermission.EVERYONE.name()
        );
    }
}
