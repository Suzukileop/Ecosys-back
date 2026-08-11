package com.plateforme.scheduler.job;

import com.plateforme.scheduler.service.SchedulerEcosystemService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PublicationJobTest {

    @Mock
    private SchedulerEcosystemService schedulerEcosystemService;

    @InjectMocks
    private PublicationJob publicationJob;

    @Test
    @DisplayName("execute : délègue à processScheduledPosts")
    void execute_delegatesToProcessScheduledPosts() {
        publicationJob.execute();
        verify(schedulerEcosystemService).processScheduledPosts();
    }
}
