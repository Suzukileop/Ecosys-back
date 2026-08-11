package com.plateforme.scheduler.dto;

import java.util.List;
import java.util.Map;

public record AnalyticsDashboardResponse(
        long totalScheduled,
        long totalPublished,
        long totalFailed,
        long totalCancelled,
        long totalViews,
        long totalLikes,
        Map<String, Long> postsByPlatform,
        List<DailyStats> last30Days,
        double successRate
) {}
