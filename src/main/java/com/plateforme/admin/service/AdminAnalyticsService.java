package com.plateforme.admin.service;

import com.plateforme.admin.dto.AdminGlobalStatsResponse;
import com.plateforme.user.repository.CreatorProfileRepository;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAnalyticsService {

    private final UserRepository userRepository;
    private final CreatorProfileRepository creatorProfileRepository;

    @Transactional(readOnly = true)
    public AdminGlobalStatsResponse getGlobalStats() {
        return new AdminGlobalStatsResponse(
                userRepository.countByDeletedAtIsNull(),
                creatorProfileRepository.count());
    }
}
