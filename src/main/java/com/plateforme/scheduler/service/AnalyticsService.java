package com.plateforme.scheduler.service;

import com.plateforme.marketplace.repository.ContentPostRepository;
import com.plateforme.scheduler.dto.AnalyticsDashboardResponse;
import com.plateforme.scheduler.dto.CreatorAnalyticsResponse;
import com.plateforme.scheduler.dto.DailyStats;
import com.plateforme.scheduler.entity.PostStatus;
import com.plateforme.scheduler.entity.PublicationAnalytics;
import com.plateforme.scheduler.entity.ScheduledPost;
import com.plateforme.scheduler.repository.PublicationAnalyticsRepository;
import com.plateforme.scheduler.repository.ScheduledPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final ScheduledPostRepository scheduledPostRepository;
    private final PublicationAnalyticsRepository publicationAnalyticsRepository;
    private final ContentPostRepository contentPostRepository;

    @Transactional(readOnly = true)
    public AnalyticsDashboardResponse getDashboard(UUID clientId) {
        long totalScheduled = scheduledPostRepository.countByClient_IdAndStatus(clientId, PostStatus.SCHEDULED);
        long totalPublished = scheduledPostRepository.countByClient_IdAndStatus(clientId, PostStatus.PUBLISHED);
        long totalFailed = scheduledPostRepository.countByClient_IdAndStatus(clientId, PostStatus.FAILED);
        long totalCancelled = scheduledPostRepository.countByClient_IdAndStatus(clientId, PostStatus.CANCELLED);

        long totalViews = Optional.ofNullable(publicationAnalyticsRepository.sumViewsByClientId(clientId)).orElse(0L);
        long totalLikes = Optional.ofNullable(publicationAnalyticsRepository.sumLikesByClientId(clientId)).orElse(0L);

        Map<String, Long> postsByPlatform = scheduledPostRepository.countPostsByPlatformForClient(clientId)
                .stream()
                .collect(Collectors.toMap(
                        row -> ((Enum<?>) row[0]).name(),
                        row -> (Long) row[1],
                        Long::sum
                ));

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(29);
        LocalDateTime fromTs = start.atStartOfDay();
        LocalDateTime toTs = end.plusDays(1).atStartOfDay();

        List<ScheduledPost> publishedWindow = scheduledPostRepository
                .findByClient_IdAndStatusAndPublishedAtBetween(clientId, PostStatus.PUBLISHED, fromTs, toTs);

        List<UUID> postIds = publishedWindow.stream().map(ScheduledPost::getId).toList();
        Map<UUID, PublicationAnalytics> analyticsByPost = postIds.isEmpty()
                ? Map.of()
                : publicationAnalyticsRepository.findByPost_IdIn(postIds)
                .stream()
                .collect(Collectors.toMap(a -> a.getPost().getId(), a -> a, (a, b) -> a));

        Map<LocalDate, long[]> dayStats = new HashMap<>();
        for (ScheduledPost p : publishedWindow) {
            if (p.getPublishedAt() == null) {
                continue;
            }
            LocalDate d = p.getPublishedAt().toLocalDate();
            if (d.isBefore(start) || d.isAfter(end)) {
                continue;
            }
            long[] arr = dayStats.computeIfAbsent(d, k -> new long[]{0, 0, 0});
            arr[0] += 1;
            PublicationAnalytics pa = analyticsByPost.get(p.getId());
            if (pa != null) {
                arr[1] += pa.getViews() != null ? pa.getViews() : 0;
                arr[2] += pa.getLikes() != null ? pa.getLikes() : 0;
            }
        }

        List<DailyStats> last30Days = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            long[] arr = dayStats.getOrDefault(d, new long[]{0, 0, 0});
            last30Days.add(new DailyStats(d, (int) arr[0], (int) arr[1], (int) arr[2]));
        }

        double successRate = (totalPublished + totalFailed) == 0
                ? 0.0
                : (double) totalPublished / (totalPublished + totalFailed) * 100.0;

        return new AnalyticsDashboardResponse(
                totalScheduled,
                totalPublished,
                totalFailed,
                totalCancelled,
                totalViews,
                totalLikes,
                postsByPlatform,
                last30Days,
                successRate
        );
    }

    @Transactional(readOnly = true)
    public CreatorAnalyticsResponse getCreatorAnalytics(UUID creatorId) {
        long totalContentPosts = contentPostRepository.countByCreator_Id(creatorId);
        long totalViews = Optional.ofNullable(contentPostRepository.sumViewsByCreatorId(creatorId)).orElse(0L);
        long totalLikes = Optional.ofNullable(contentPostRepository.sumLikesByCreatorId(creatorId)).orElse(0L);

        return new CreatorAnalyticsResponse(totalContentPosts, totalViews, totalLikes);
    }
}
