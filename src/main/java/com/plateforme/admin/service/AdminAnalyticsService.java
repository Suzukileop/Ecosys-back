package com.plateforme.admin.service;

import com.plateforme.admin.dto.AdminGlobalStatsResponse;
import com.plateforme.scheduler.entity.PostStatus;
import com.plateforme.scheduler.repository.ScheduledPostRepository;
import com.plateforme.user.repository.CreatorProfileRepository;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAnalyticsService {

    private final UserRepository userRepository;
    private final ScheduledPostRepository scheduledPostRepository;
    private final CreatorProfileRepository creatorProfileRepository;

    @Transactional(readOnly = true)
    public AdminGlobalStatsResponse getGlobalStats() {
        long users = userRepository.countByDeletedAtIsNull();
        long published = scheduledPostRepository.countByStatus(PostStatus.PUBLISHED);
        long creators = creatorProfileRepository.count();

        return new AdminGlobalStatsResponse(users, published, creators, 0L);
    }
}
