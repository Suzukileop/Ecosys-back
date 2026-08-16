package com.plateforme.marketplace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plateforme.ecosystem.storage.PublicMediaUrlResolver;
import com.plateforme.marketplace.repository.ContentPostRepository;
import com.plateforme.marketplace.repository.MarketplaceProductRepository;
import com.plateforme.user.repository.CreatorFollowRepository;
import com.plateforme.user.repository.CreatorProfileRepository;
import com.plateforme.user.repository.CreatorProfileVisitRepository;
import com.plateforme.user.service.CreatorFollowService;
import com.plateforme.user.service.CreatorPortfolioService;
import com.plateforme.user.service.CreatorReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Documents ranking-related query expansion: specialties/tags relevance is scored in SQL;
 * this suite asserts the service passes expanded terms and verified filters correctly.
 */
@ExtendWith(MockitoExtension.class)
class MarketplaceCreatorSearchRelevanceTest {

    @Mock CreatorProfileRepository creatorProfileRepository;
    @Mock CreatorProfileVisitRepository creatorProfileVisitRepository;
    @Mock ContentPostRepository contentPostRepository;
    @Mock MarketplaceProductRepository productRepository;
    @Mock CreatorPortfolioService creatorPortfolioService;
    @Mock ObjectMapper objectMapper;
    @Mock CreatorReviewService creatorReviewService;
    @Mock CreatorFollowService creatorFollowService;
    @Mock CreatorFollowRepository creatorFollowRepository;
    @Mock PublicMediaUrlResolver publicMediaUrlResolver;

    MarketplaceService marketplaceService;
    Pageable pageable;

    @BeforeEach
    void setUp() {
        marketplaceService = new MarketplaceService(
                creatorProfileRepository,
                creatorProfileVisitRepository,
                contentPostRepository,
                productRepository,
                creatorPortfolioService,
                objectMapper,
                creatorReviewService,
                creatorFollowService,
                creatorFollowRepository,
                publicMediaUrlResolver);
        pageable = PageRequest.of(0, 12);
        when(creatorFollowService.getFollowedCreatorIds(any(), any())).thenReturn(Set.of());
        when(creatorFollowService.getFollowerCounts(any())).thenReturn(Map.of());
    }

    @Test
    @DisplayName("q=dev expands to Developer for exact specialty ranking")
    void search_expandsDevAliasToDeveloperCanonical() {
        when(creatorProfileRepository.searchByBioOrSpecialite(
                        anyString(), anyString(), any(), any(), any(), any(), anyString(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        marketplaceService.searchCreators(
                "dev", true, null, null, null, null, null, null, null, null, pageable);

        ArgumentCaptor<String> qCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> qCanonCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> verifiedCap = ArgumentCaptor.forClass(Boolean.class);
        verify(creatorProfileRepository).searchByBioOrSpecialite(
                qCap.capture(),
                qCanonCap.capture(),
                verifiedCap.capture(),
                isNull(),
                isNull(),
                isNull(),
                eq(""),
                isNull(),
                eq(pageable));

        assertThat(qCap.getValue()).isEqualTo("dev");
        assertThat(qCanonCap.getValue()).isEqualTo("Developer");
        assertThat(verifiedCap.getValue()).isTrue();
    }

    @Test
    @DisplayName("Popular chip specialite=dev prefers canonical Developer + alt raw")
    void list_expandsSpecialtyChipForExactFirstRanking() {
        when(creatorProfileRepository.findForMarketplace(
                        any(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        marketplaceService.getCreators(
                "dev", null, null, null, null, null, null, null, null, pageable);

        ArgumentCaptor<String> primaryCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> altCap = ArgumentCaptor.forClass(String.class);
        verify(creatorProfileRepository).findForMarketplace(
                primaryCap.capture(),
                altCap.capture(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(pageable));

        assertThat(primaryCap.getValue()).isEqualTo("Developer");
        assertThat(altCap.getValue()).isEqualTo("dev");
    }

    @Test
    @DisplayName("blank keyword falls back to list path with verified")
    void search_blankKeywordUsesListWithVerified() {
        when(creatorProfileRepository.findForMarketplace(
                        any(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        marketplaceService.searchCreators(
                "  ", true, null, null, "Data science", null, null, null, null, null, pageable);

        verify(creatorProfileRepository).findForMarketplace(
                eq("Data science"),
                eq(""),
                eq(true),
                isNull(),
                isNull(),
                isNull(),
                eq(pageable));
    }
}
