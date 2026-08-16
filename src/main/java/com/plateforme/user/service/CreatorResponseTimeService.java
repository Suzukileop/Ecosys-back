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

    /** Live Discuss metrics for the public trust strip (not cached on CreatorProfile). */
    public record DiscussResponseMetrics(
            long inboundConversationCount,
            long repliedConversationCount,
            Integer responseRatePercent,
            String typicallyRepliesWithinLabel
    ) {
        public static DiscussResponseMetrics empty() {
            return new DiscussResponseMetrics(0, 0, null, null);
        }
    }

    @Transactional(readOnly = true)
    public DiscussResponseMetrics computeDiscussResponseMetrics(UUID creatorUserId) {
        List<Object[]> rows = directMessageRepository.aggregateDiscussResponseStats(creatorUserId);
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return DiscussResponseMetrics.empty();
        }
        Object[] row = rows.get(0);
        long inbound = toLong(row.length > 0 ? row[0] : null);
        long replied = toLong(row.length > 1 ? row[1] : null);
        Double avgSeconds = row.length > 2 && row[2] instanceof Number n ? n.doubleValue() : null;

        Integer rate = inbound <= 0 ? null : (int) Math.round((replied * 100.0) / inbound);
        String label = replied > 0 && avgSeconds != null && avgSeconds >= 0
                ? formatTypicallyRepliesWithin((int) Math.round(avgSeconds))
                : null;
        return new DiscussResponseMetrics(inbound, replied, rate, label);
    }

    /**
     * Human-readable average first-reply latency for the Response rate card hint.
     * &lt;1h → minutes, &lt;24h → hours, else → days.
     */
    public static String formatTypicallyRepliesWithin(Integer avgSeconds) {
        if (avgSeconds == null || avgSeconds < 0) {
            return null;
        }
        if (avgSeconds < 3600) {
            int minutes = Math.max(1, (int) Math.round(avgSeconds / 60.0));
            return "Typically replies within " + minutes + " min";
        }
        if (avgSeconds < 86_400) {
            int hours = Math.max(1, (int) Math.round(avgSeconds / 3600.0));
            return "Typically replies within " + hours + " h";
        }
        int days = Math.max(1, (int) Math.round(avgSeconds / 86_400.0));
        return "Typically replies within " + days + (days == 1 ? " day" : " days");
    }

    private static long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }

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
