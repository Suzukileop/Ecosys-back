package com.plateforme.marketplace.controller;

import com.plateforme.marketplace.dto.CreatorProfileResponse;
import com.plateforme.marketplace.dto.CreatorProfileViewResponse;
import com.plateforme.marketplace.service.ContentPostService;
import com.plateforme.marketplace.service.CreatorProfileViewService;
import com.plateforme.marketplace.service.MarketplaceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketplaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketplaceService marketplaceService;

    @MockBean
    private ContentPostService contentPostService;

    @MockBean
    private CreatorProfileViewService creatorProfileViewService;

    @Test
    @DisplayName("GET /creators sans JWT → 200")
    void getCreators_noAuth_200() throws Exception {
        UUID uid = UUID.randomUUID();
        CreatorProfileResponse row = new CreatorProfileResponse(
                uid,
                "Name",
                "name",
                null,
                null,
                "bio",
                "specialite",
                List.of(),
                List.of(),
                null,
                List.of(),
                false,
                true,
                null,
                0L,
                0L,
                0L,
                0L,
                null,
                "BANNER",
                "DEFAULT",
                "LEFT",
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                0L,
                false,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                null,
                List.of(),
                0L,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                null,
                null,
                null,
                List.of(),
                Map.of(),
                null,
                null,
                null,
                null,
                null);

        when(marketplaceService.getCreators(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/marketplace/creators").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(uid.toString()));
    }

    @Test
    @DisplayName("GET /contents avec genre → délègue au service")
    void getPublicContents_filterByGenre() throws Exception {
        when(contentPostService.getPublicPosts(isNull(), eq("Tech"), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/marketplace/contents").param("genre", "Tech"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("POST /creators/{id}/view sans JWT → 200")
    void recordCreatorProfileView_noAuth_200() throws Exception {
        UUID creatorId = UUID.randomUUID();
        when(creatorProfileViewService.recordView(eq(creatorId), isNull(), eq("anon-key")))
                .thenReturn(new CreatorProfileViewResponse(true, 3L));

        mockMvc.perform(post("/api/marketplace/creators/{id}/view", creatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visitorKey\":\"anon-key\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recorded").value(true))
                .andExpect(jsonPath("$.profileVisits").value(3));
    }
}
