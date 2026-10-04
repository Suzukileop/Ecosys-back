package com.plateforme.user.service;

import com.plateforme.user.dto.UpdateUserSettingsDto;
import com.plateforme.user.entity.UserSettings;
import com.plateforme.user.entity.UserSettings.MessagePermission;
import com.plateforme.user.repository.CreatorFollowRepository;
import com.plateforme.user.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSettingsServiceTest {

    @Mock UserSettingsRepository userSettingsRepository;
    @Mock CreatorFollowRepository creatorFollowRepository;
    @InjectMocks UserSettingsService service;

    UUID userId;
    UUID otherId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otherId = UUID.randomUUID();
    }

    @Test
    void defaultsArePermissiveWhenNoRowExists() {
        when(userSettingsRepository.findById(userId)).thenReturn(Optional.empty());
        assertThat(service.isNotificationMuted(userId, "CREATOR_NEW_FOLLOWER")).isFalse();
        assertThat(service.hidesOnlineStatus(userId)).isFalse();
        assertThat(service.browsesPrivately(userId)).isFalse();
        assertThat(service.rejectsNewMessagesFrom(userId, otherId)).isFalse();
    }

    @Test
    void mutedTypeIsSkippedButTransactionalTypesNeverAre() {
        UserSettings settings = new UserSettings(userId);
        settings.setNotifyFollowers(false);
        settings.setNotifySales(false);
        when(userSettingsRepository.findById(userId)).thenReturn(Optional.of(settings));

        assertThat(service.isNotificationMuted(userId, "CREATOR_NEW_FOLLOWER")).isTrue();
        assertThat(service.isNotificationMuted(userId, "MARKETPLACE_SALE")).isTrue();
        assertThat(service.isNotificationMuted(userId, "MARKETPLACE_PURCHASE")).isFalse();
    }

    @Test
    void followingPermissionOnlyAcceptsPeopleTheUserFollows() {
        UserSettings settings = new UserSettings(userId);
        settings.setMessagePermission(MessagePermission.FOLLOWING);
        when(userSettingsRepository.findById(userId)).thenReturn(Optional.of(settings));
        when(creatorFollowRepository.existsByFollower_IdAndCreator_Id(userId, otherId)).thenReturn(false);

        assertThat(service.rejectsNewMessagesFrom(userId, otherId)).isTrue();
    }

    @Test
    void partialUpdateOnlyTouchesProvidedFields() {
        when(userSettingsRepository.findById(userId)).thenReturn(Optional.empty());
        when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.updateSettings(userId, new UpdateUserSettingsDto(
                null, null, null, null, null, null, null, false, null, "NOBODY"));

        assertThat(result.showOnlineStatus()).isFalse();
        assertThat(result.messagePermission()).isEqualTo("NOBODY");
        assertThat(result.notifyComments()).isTrue();
    }
}
