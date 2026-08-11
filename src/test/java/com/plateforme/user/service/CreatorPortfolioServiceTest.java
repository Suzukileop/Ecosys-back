package com.plateforme.user.service;

import com.plateforme.marketplace.dto.ContentPostResponse;
import com.plateforme.marketplace.entity.ContentPost;
import com.plateforme.marketplace.repository.ContentPostRepository;
import com.plateforme.marketplace.service.ContentPostService;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.dto.UpdatePortfolioRequest;
import com.plateforme.user.entity.CreatorPortfolioPost;
import com.plateforme.user.entity.Role;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.CreatorPortfolioPostRepository;
import com.plateforme.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatorPortfolioServiceTest {

    @Mock
    private CreatorPortfolioPostRepository portfolioPostRepository;
    @Mock
    private ContentPostRepository contentPostRepository;
    @Mock
    private ContentPostService contentPostService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CreatorPortfolioService creatorPortfolioService;

    private UUID creatorId;
    private User creator;
    private ContentPost post;

    @BeforeEach
    void setUp() {
        creatorId = UUID.randomUUID();
        creator = new User();
        creator.setId(creatorId);
        Role role = new Role();
        role.setName("ROLE_CREATOR");
        creator.setRoles(Set.of(role));

        post = new ContentPost();
        post.setId(UUID.randomUUID());
        post.setCreator(creator);
    }

    @Test
    @DisplayName("updateCuratedPosts rejects posts not owned by creator")
    void updateCuratedPosts_rejectsForeignPosts() {
        UUID foreignPostId = UUID.randomUUID();
        when(userRepository.findByIdAndDeletedAtIsNull(creatorId)).thenReturn(Optional.of(creator));
        when(contentPostRepository.findAllById(List.of(foreignPostId))).thenReturn(List.of());

        assertThatThrownBy(() -> creatorPortfolioService.updateCuratedPosts(
                creatorId, new UpdatePortfolioRequest(List.of(foreignPostId))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("updateCuratedPosts saves ordered entries")
    void updateCuratedPosts_savesOrder() {
        when(userRepository.findByIdAndDeletedAtIsNull(creatorId)).thenReturn(Optional.of(creator));
        when(contentPostRepository.findAllById(List.of(post.getId()))).thenReturn(List.of(post));
        CreatorPortfolioPost entry = new CreatorPortfolioPost(creator, post, 0);
        when(portfolioPostRepository.countPublicCuratedByCreatorId(creatorId)).thenReturn(1L);
        when(portfolioPostRepository.findActiveCuratedByCreatorId(creatorId)).thenReturn(List.of(entry));
        when(contentPostService.toResponse(any(ContentPost.class), anyLong()))
                .thenReturn(new ContentPostResponse(
                        post.getId(), "Title", null, null, "FILE", null, null, null,
                        List.of(), null, null, List.of(), List.of(), true, true, false, null,
                        0, 0, 1L, null, null));

        List<ContentPostResponse> result = creatorPortfolioService.updateCuratedPosts(
                creatorId, new UpdatePortfolioRequest(List.of(post.getId())));

        verify(portfolioPostRepository).deleteAllByCreatorUserId(creatorId);
        verify(portfolioPostRepository).saveAll(anyList());
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("updateCuratedPosts rejects more than 2 items")
    void updateCuratedPosts_rejectsTooMany() {
        List<UUID> ids = java.util.stream.IntStream.range(0, 3)
                .mapToObj(i -> UUID.randomUUID())
                .toList();
        when(userRepository.findByIdAndDeletedAtIsNull(creatorId)).thenReturn(Optional.of(creator));

        assertThatThrownBy(() -> creatorPortfolioService.updateCuratedPosts(
                creatorId, new UpdatePortfolioRequest(ids)))
                .isInstanceOf(BusinessException.class);
    }
}
