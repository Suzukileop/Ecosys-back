package com.plateforme.auth.job;

import com.plateforme.user.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Every login and every access-token refresh inserts a refresh token; without this job the table
 * grows by roughly a hundred rows per active user per day.
 *
 * <p>Revoked tokens are kept for a day so a client replaying a just-rotated token still gets the
 * explicit "session ended" error rather than "invalid session".
 */
@Component
@ConditionalOnProperty(prefix = "scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenPurgeJob {

    private static final long REVOKED_RETENTION_HOURS = 24;

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT1H")
    @Transactional
    public void purge() {
        LocalDateTime now = LocalDateTime.now();
        int deleted = refreshTokenRepository.deleteStale(now, now.minusHours(REVOKED_RETENTION_HOURS));
        if (deleted > 0) {
            log.info("Purged {} stale refresh tokens", deleted);
        }
    }
}
