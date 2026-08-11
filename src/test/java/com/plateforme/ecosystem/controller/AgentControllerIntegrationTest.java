package com.plateforme.ecosystem.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plateforme.ecosystem.dto.AgentProposeDto;
import com.plateforme.ecosystem.dto.NicheRequestResponse;
import com.plateforme.ecosystem.service.AgentEcosystemService;
import com.plateforme.ecosystem.service.DeepSeekBotService;
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
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AgentEcosystemService agentEcosystemService;

    @MockBean
    private DeepSeekBotService deepSeekBotService;

    @MockBean
    private RedisTemplate<String, String> redisTemplate;

    private NicheRequestResponse buildResponse(UUID id) {
        return new NicheRequestResponse(
                id,
                "MCT-TEST",
                "Fitness",
                "Description",
                "FR",
                3,
                List.of("INSTAGRAM"),
                null,
                null,
                null,
                null,
                25000,
                "250,00 €",
                "PENDING",
                "UNPAID",
                true,
                null,
                null,
                LocalDateTime.now(),
                null,
                null,
                "WAITING_AGENT",
                null,
                null,
                null,
                "c@test.com",
                "Client"
        );
    }

    @Test
    @DisplayName("GET /api/agent/niche-requests avec rôle AGENT → 200")
    @WithMockUser(roles = "AGENT")
    void getNicheRequests_asAgent_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(agentEcosystemService.getPendingRequests(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(buildResponse(id))));

        mockMvc.perform(get("/api/agent/niche-requests")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/agent/niche-requests sans authentification → 401")
    void getNicheRequests_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/agent/niche-requests"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/agent/niche-requests avec rôle CLIENT → 403")
    @WithMockUser(roles = "CLIENT")
    void getNicheRequests_asClient_returns403() throws Exception {
        mockMvc.perform(get("/api/agent/niche-requests"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/agent/niche-requests/{id}/bot-history avec rôle AGENT → 200")
    @WithMockUser(roles = "AGENT")
    void getBotHistory_asAgent_returns200() throws Exception {
        UUID rid = UUID.randomUUID();
        when(deepSeekBotService.getBotHistoryForAgent(rid)).thenReturn(List.of());

        mockMvc.perform(get("/api/agent/niche-requests/{id}/bot-history", rid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("PUT /api/agent/niche-requests/{id}/propose avec rôle AGENT → 200")
    void proposeModel_asAgent_returns200() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        User agentPrincipal = new User();
        agentPrincipal.setId(agentId);
        agentPrincipal.setEmail("agent@test.com");
        agentPrincipal.setPasswordHash("hash");
        agentPrincipal.setRoles(Set.of(new Role("ROLE_AGENT")));

        AgentProposeDto dto = new AgentProposeDto("https://cdn.example.com/demo.mp4", "Notes");

        NicheRequestResponse response = new NicheRequestResponse(
                requestId,
                "MCT-ZZ99",
                "Fitness",
                "D",
                "FR",
                3,
                List.of("TIKTOK"),
                null,
                null,
                null,
                null,
                10000,
                "100,00 €",
                "PROPOSED",
                "UNPAID",
                true,
                "https://cdn.example.com/demo.mp4",
                "Notes",
                LocalDateTime.now(),
                null,
                null,
                "VALIDATE_MODEL",
                null,
                null,
                agentId,
                "c@test.com",
                "Client"
        );

        when(agentEcosystemService.proposeModel(eq(requestId), eq(agentId), any(AgentProposeDto.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/agent/niche-requests/{id}/propose", requestId)
                        .with(user(agentPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROPOSED"))
                .andExpect(jsonPath("$.demoContentUrl").value("https://cdn.example.com/demo.mp4"));
    }

    @Test
    @DisplayName("PUT /api/agent/niche-requests/{id}/propose avec body invalide → 400")
    @WithMockUser(roles = "AGENT")
    void proposeModel_invalidBody_returns400() throws Exception {
        UUID requestId = UUID.randomUUID();
        AgentProposeDto invalidDto = new AgentProposeDto("", null);

        mockMvc.perform(put("/api/agent/niche-requests/{id}/propose", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest());
    }
}
