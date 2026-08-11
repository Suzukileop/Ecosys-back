package com.plateforme.user.service;

import com.plateforme.messaging.repository.DirectMessageRepository;
import com.plateforme.user.entity.CreatorProfile;
import com.plateforme.user.repository.CreatorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreatorResponseTimeService {

    private static final int MAX_SAMPLES = 30;

    private final DirectMessageRepository directMessageRepository;
    private final CreatorProfileRepository creatorProfileRepository;

    @Async
    public void recalculateAsync(UUID creatorUserId) {
        try {
            recalculateAndCache(creatorUserId);
        } catch (Exception e) {
            log.warn("Failed to recalculate response time for creator={}: {}", creatorUserId, e.getMessage());
        }
    }

    @Transactional
    public void recalculateAndCache(UUID creatorUserId) {
        CreatorProfile profile = creatorProfileRepository.findByUserId(creatorUserId).orElse(null);
        if (profile == null) {
            return;
        }

        List<Double> samples = directMessageRepository.findRecentResponseTimeSeconds(creatorUserId, MAX_SAMPLES);
        if (samples.isEmpty()) {
            profile.setAvgResponseTimeSeconds(null);
            profile.setResponseTimeSampleCount(0);
            profile.setResponseTimeComputedAt(LocalDateTime.now());
            creatorProfileRepository.save(profile);
            log.debug("No response time samples for creator={}", creatorUserId);
            return;
        }

        long totalSeconds = 0;
        for (Double sample : samples) {
            if (sample != null && sample >= 0) {
                totalSeconds += sample.longValue();
            }
        }
        int count = samples.size();
        int avgSeconds = count > 0 ? (int) Math.round((double) totalSeconds / count) : 0;

        profile.setAvgResponseTimeSeconds(avgSeconds);
        profile.setResponseTimeSampleCount(count);
        profile.setResponseTimeComputedAt(LocalDateTime.now());
        creatorProfileRepository.save(profile);
        log.debug("Response time cached for creator={}: avg={}s samples={}", creatorUserId, avgSeconds, count);
    }

    public static final String WITHIN_1_HOUR = "WITHIN_1_HOUR";
    public static final String FEW_HOURS = "FEW_HOURS";
    public static final String WITHIN_DAY = "WITHIN_DAY";
    public static final String WITHIN_2_3_DAYS = "WITHIN_2_3_DAYS";

    public static String normalizeTypicalResponseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String code = raw.trim();
        return switch (code) {
            case WITHIN_1_HOUR, FEW_HOURS, WITHIN_DAY, WITHIN_2_3_DAYS -> code;
            default -> null;
        };
    }

    public static String labelForTypicalResponseTime(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return switch (code.trim()) {
            case WITHIN_1_HOUR -> "Usually within 1 hour";
            case FEW_HOURS -> "Usually within a few hours";
            case WITHIN_DAY -> "Usually within a day";
            case WITHIN_2_3_DAYS -> "Usually within 2-3 days";
            default -> null;
        };
    }

    public static String resolveResponseTimeLabel(
            String typicalResponseTime, Integer avgSeconds, Integer sampleCount) {
        String manualLabel = labelForTypicalResponseTime(typicalResponseTime);
        if (manualLabel != null) {
            return manualLabel;
        }
        return formatResponseTimeLabel(avgSeconds, sampleCount);
    }

    public static String formatResponseTimeLabel(Integer avgSeconds, Integer sampleCount) {
        if (avgSeconds == null || sampleCount == null || sampleCount < ProfileExtensionsSupport.MIN_SAMPLES_FOR_LABEL) {
            return null;
        }
        if (avgSeconds < 3600) {
            return "Usually within 1 hour";
        }
        if (avgSeconds < 21_600) {
            return "Usually within a few hours";
        }
        if (avgSeconds < 86_400) {
            return "Usually within a day";
        }
        return "Usually within 2-3 days";
    }
}
