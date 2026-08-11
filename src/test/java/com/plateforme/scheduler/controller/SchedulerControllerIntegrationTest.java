package com.plateforme.scheduler.controller;

import com.plateforme.scheduler.dto.ScheduledPostResponse;
import com.plateforme.scheduler.entity.ContentType;
import com.plateforme.scheduler.entity.Platform;
import com.plateforme.scheduler.entity.PostStatus;
import com.plateforme.scheduler.service.SchedulerEcosystemService;
import com.plateforme.user.entity.Role;
import com.plateforme.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SchedulerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SchedulerEcosystemService schedulerEcosystemService;

    @MockBean
    private RedisTemplate<String, String> redisTemplate;

    @Test
    @DisplayName("GET /api/scheduler/posts/niche/{id} avec rôle CREATOR → 200")
    void getNichePosts_asCreator_returns200() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        User clientPrincipal = new User();
        clientPrincipal.setId(clientId);
        clientPrincipal.setEmail("client@test.com");
        clientPrincipal.setPasswordHash("hash");
        clientPrincipal.setRoles(Set.of(new Role("ROLE_CREATOR")));

        ScheduledPostResponse post = new ScheduledPostResponse(
                UUID.randomUUID(),
                Platform.TIKTOK,
                "https://cdn.example.com/video.mp4",
                ContentType.EXTERNAL_URL,
                "Caption",
                "NCT-812A",
                1,
                LocalDateTime.now().plusDays(1),
                PostStatus.SCHEDULED,
                null,
                null,
                LocalDateTime.now()
        );

        when(schedulerEcosystemService.getPostsByNicheRequest(
                eq(clientId), eq(requestId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post)));

        mockMvc.perform(get("/api/scheduler/posts/niche/{id}", requestId)
                        .with(user(clientPrincipal))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].platform").value("TIKTOK"))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    @DisplayName("GET /api/scheduler/posts/niche/{id} sans authentification → 401")
    void getNichePosts_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/scheduler/posts/niche/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/scheduler/posts/niche/{id} avec rôle AGENT → 403")
    @WithMockUser(roles = "AGENT")
    void getNichePosts_asAgent_returns403() throws Exception {
        mockMvc.perform(get("/api/scheduler/posts/niche/{id}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }
}
