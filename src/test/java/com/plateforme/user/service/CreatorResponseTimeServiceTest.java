package com.plateforme.user.service;

import com.plateforme.messaging.repository.DirectMessageRepository;
import com.plateforme.user.entity.CreatorProfile;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.CreatorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatorResponseTimeServiceTest {

    @Mock
    private DirectMessageRepository directMessageRepository;
    @Mock
    private CreatorProfileRepository creatorProfileRepository;

    @InjectMocks
    private CreatorResponseTimeService creatorResponseTimeService;

    private UUID creatorId;
    private CreatorProfile profile;

    @BeforeEach
    void setUp() {
        creatorId = UUID.randomUUID();
        User user = new User();
        user.setId(creatorId);
        profile = new CreatorProfile();
        profile.setUser(user);
    }

    @Test
    @DisplayName("recalculateAndCache stores average from repository samples")
    void recalculateAndCache_computesAverage() {
        when(creatorProfileRepository.findByUserId(creatorId)).thenReturn(Optional.of(profile));
        when(directMessageRepository.findRecentResponseTimeSeconds(eq(creatorId), eq(30)))
                .thenReturn(List.of(3600.0, 7200.0));

        creatorResponseTimeService.recalculateAndCache(creatorId);

        ArgumentCaptor<CreatorProfile> captor = ArgumentCaptor.forClass(CreatorProfile.class);
        verify(creatorProfileRepository).save(captor.capture());
        CreatorProfile saved = captor.getValue();
        assertThat(saved.getAvgResponseTimeSeconds()).isEqualTo(5400);
        assertThat(saved.getResponseTimeSampleCount()).isEqualTo(2);
        assertThat(saved.getResponseTimeComputedAt()).isNotNull();
    }

    @Test
    @DisplayName("formatResponseTimeLabel returns null below minimum samples")
    void formatResponseTimeLabel_requiresMinimumSamples() {
        assertThat(CreatorResponseTimeService.formatResponseTimeLabel(120, 0)).isNull();
        assertThat(CreatorResponseTimeService.formatResponseTimeLabel(null, 1)).isNull();
    }

    @Test
    @DisplayName("formatResponseTimeLabel returns English buckets")
    void formatResponseTimeLabel_englishBuckets() {
        assertThat(CreatorResponseTimeService.formatResponseTimeLabel(900, 1))
                .isEqualTo("Usually within 1 hour");
        assertThat(CreatorResponseTimeService.formatResponseTimeLabel(7200, 5))
                .isEqualTo("Usually within a few hours");
        assertThat(CreatorResponseTimeService.formatResponseTimeLabel(50_000, 5))
                .isEqualTo("Usually within a day");
        assertThat(CreatorResponseTimeService.formatResponseTimeLabel(100_000, 5))
                .isEqualTo("Usually within 2-3 days");
        assertThat(CreatorResponseTimeService.formatResponseTimeLabel(200_000, 5))
                .isEqualTo("Usually within 2-3 days");
    }

    @Test
    @DisplayName("normalizeTypicalResponseTime accepts presets and clears blank")
    void normalizeTypicalResponseTime_presetsAndBlank() {
        assertThat(CreatorResponseTimeService.normalizeTypicalResponseTime("WITHIN_1_HOUR"))
                .isEqualTo("WITHIN_1_HOUR");
        assertThat(CreatorResponseTimeService.normalizeTypicalResponseTime(" FEW_HOURS "))
                .isEqualTo("FEW_HOURS");
        assertThat(CreatorResponseTimeService.normalizeTypicalResponseTime("WITHIN_DAY"))
                .isEqualTo("WITHIN_DAY");
        assertThat(CreatorResponseTimeService.normalizeTypicalResponseTime("WITHIN_2_3_DAYS"))
                .isEqualTo("WITHIN_2_3_DAYS");
        assertThat(CreatorResponseTimeService.normalizeTypicalResponseTime("")).isNull();
        assertThat(CreatorResponseTimeService.normalizeTypicalResponseTime("  ")).isNull();
        assertThat(CreatorResponseTimeService.normalizeTypicalResponseTime(null)).isNull();
        assertThat(CreatorResponseTimeService.normalizeTypicalResponseTime("INVALID")).isNull();
    }

    @Test
    @DisplayName("labelForTypicalResponseTime maps English labels")
    void labelForTypicalResponseTime_englishLabels() {
        assertThat(CreatorResponseTimeService.labelForTypicalResponseTime("WITHIN_1_HOUR"))
                .isEqualTo("Usually within 1 hour");
        assertThat(CreatorResponseTimeService.labelForTypicalResponseTime("FEW_HOURS"))
                .isEqualTo("Usually within a few hours");
        assertThat(CreatorResponseTimeService.labelForTypicalResponseTime("WITHIN_DAY"))
                .isEqualTo("Usually within a day");
        assertThat(CreatorResponseTimeService.labelForTypicalResponseTime("WITHIN_2_3_DAYS"))
                .isEqualTo("Usually within 2-3 days");
        assertThat(CreatorResponseTimeService.labelForTypicalResponseTime(null)).isNull();
        assertThat(CreatorResponseTimeService.labelForTypicalResponseTime("UNKNOWN")).isNull();
    }

    @Test
    @DisplayName("resolveResponseTimeLabel prefers manual over computed DM latency")
    void resolveResponseTimeLabel_prefersManual() {
        assertThat(CreatorResponseTimeService.resolveResponseTimeLabel("WITHIN_DAY", 900, 5))
                .isEqualTo("Usually within a day");
        assertThat(CreatorResponseTimeService.resolveResponseTimeLabel(null, 900, 5))
                .isEqualTo("Usually within 1 hour");
        assertThat(CreatorResponseTimeService.resolveResponseTimeLabel("", 900, 5))
                .isEqualTo("Usually within 1 hour");
        assertThat(CreatorResponseTimeService.resolveResponseTimeLabel(null, 900, 0)).isNull();
    }

    @Test
    @DisplayName("formatTypicallyRepliesWithin uses min / h / days buckets")
    void formatTypicallyRepliesWithin_buckets() {
        assertThat(CreatorResponseTimeService.formatTypicallyRepliesWithin(90))
                .isEqualTo("Typically replies within 2 min");
        assertThat(CreatorResponseTimeService.formatTypicallyRepliesWithin(7200))
                .isEqualTo("Typically replies within 2 h");
        assertThat(CreatorResponseTimeService.formatTypicallyRepliesWithin(86_400))
                .isEqualTo("Typically replies within 1 day");
        assertThat(CreatorResponseTimeService.formatTypicallyRepliesWithin(172_800))
                .isEqualTo("Typically replies within 2 days");
        assertThat(CreatorResponseTimeService.formatTypicallyRepliesWithin(null)).isNull();
    }

    @Test
    @DisplayName("computeDiscussResponseMetrics maps aggregate row to rate and label")
    void computeDiscussResponseMetrics_fromAggregate() {
        when(directMessageRepository.aggregateDiscussResponseStats(creatorId))
                .thenReturn(List.<Object[]>of(new Object[]{10L, 9L, 5400.0}));

        var metrics = creatorResponseTimeService.computeDiscussResponseMetrics(creatorId);

        assertThat(metrics.inboundConversationCount()).isEqualTo(10);
        assertThat(metrics.repliedConversationCount()).isEqualTo(9);
        assertThat(metrics.responseRatePercent()).isEqualTo(90);
        assertThat(metrics.typicallyRepliesWithinLabel()).isEqualTo("Typically replies within 2 h");
    }

    @Test
    @DisplayName("computeDiscussResponseMetrics returns empty when no inbound Discuss DMs")
    void computeDiscussResponseMetrics_empty() {
        when(directMessageRepository.aggregateDiscussResponseStats(creatorId))
                .thenReturn(List.<Object[]>of(new Object[]{0L, 0L, null}));

        var metrics = creatorResponseTimeService.computeDiscussResponseMetrics(creatorId);

        assertThat(metrics.inboundConversationCount()).isZero();
        assertThat(metrics.responseRatePercent()).isNull();
        assertThat(metrics.typicallyRepliesWithinLabel()).isNull();
    }
}
