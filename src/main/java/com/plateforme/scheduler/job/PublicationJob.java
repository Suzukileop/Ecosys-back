package com.plateforme.scheduler.job;

import com.plateforme.scheduler.service.SchedulerEcosystemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class PublicationJob {

    private final SchedulerEcosystemService schedulerEcosystemService;

    @Scheduled(cron = "0 * * * * *")
    public void execute() {
        schedulerEcosystemService.processScheduledPosts();
    }
}
