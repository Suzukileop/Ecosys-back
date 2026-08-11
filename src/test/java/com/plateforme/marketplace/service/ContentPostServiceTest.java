package com.plateforme.marketplace.service;

import com.plateforme.marketplace.dto.ContentPostRequest;
import com.plateforme.marketplace.entity.ContentPost;
import com.plateforme.marketplace.repository.ContentPostRepository;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentPostServiceTest {

    @Mock
    private ContentPostRepository contentPostRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ContentPostService contentPostService;

    private UUID creatorId;
    private User creator;

    @BeforeEach
    void setUp() {
        creatorId = UUID.randomUUID();
        creator = new User();
        creator.setId(creatorId);
        creator.setFullName("Creator");
        creator.setEmail("c@test.com");
        creator.setPasswordHash("x");
    }

    @Test
    @DisplayName("createPost : plus de 10 outils → BusinessException")
    void createPost_validatesToolsUsedMax10() {
        when(userRepository.findByIdAndDeletedAtIsNull(creatorId)).thenReturn(Optional.of(creator));

        List<String> tools = Collections.nCopies(11, "tool");

        ContentPostRequest req = new ContentPostRequest(
                "t", "g", "https://m.com/x", null, null, null, null, null, null, null, tools, null, true, true);

        assertThatThrownBy(() -> contentPostService.createPost(creatorId, req))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("TOOLS_USED_LIMIT");

        verify(contentPostRepository, never()).save(any());
    }

    @Test
    @DisplayName("deletePost : soft delete avec deletedAt")
    void deletePost_softDelete() {
        UUID postId = UUID.randomUUID();
        ContentPost post = new ContentPost();
        post.setId(postId);
        post.setCreator(creator);

        when(contentPostRepository.findById(postId)).thenReturn(Optional.of(post));

        contentPostService.deletePost(creatorId, postId);

        assertThat(post.getDeletedAt()).isNotNull();
        verify(contentPostRepository).save(post);
    }

    @Test
    @DisplayName("incrementView : incrémente les vues")
    void incrementView_incrementsCounter() {
        UUID postId = UUID.randomUUID();
        ContentPost post = new ContentPost();
        post.setId(postId);
        post.setCreator(creator);
        post.setIsPublic(true);
        post.setViews(3);

        when(contentPostRepository.findByIdAndIsPublicTrue(postId)).thenReturn(Optional.of(post));

        contentPostService.incrementView(postId);

        assertThat(post.getViews()).isEqualTo(4);
        verify(contentPostRepository).save(post);
    }

    @Test
    @DisplayName("getPublicPostById : contenu non public → BusinessException")
    void getPublicPostById_notFound() {
        UUID postId = UUID.randomUUID();
        when(contentPostRepository.findByIdAndIsPublicTrue(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contentPostService.getPublicPostById(postId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("CONTENT_POST_NOT_FOUND");
    }

    @Test
    @DisplayName("getMyPostById : mauvais propriétaire → AccessDeniedException")
    void getMyPostById_wrongOwner() {
        UUID postId = UUID.randomUUID();
        User other = new User();
        other.setId(UUID.randomUUID());

        ContentPost post = new ContentPost();
        post.setId(postId);
        post.setCreator(other);

        when(contentPostRepository.findById(postId)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> contentPostService.getMyPostById(creatorId, postId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("toResponse : portfolioCount calculé via repository")
    void portfolioCount_calculatedNotStored() {
        ContentPost post = new ContentPost();
        post.setId(UUID.randomUUID());
        post.setCreator(creator);
        post.setTitle("x");
        post.setMediaUrl("https://m.com");
        post.setIsPublic(true);
        post.setViews(1);
        post.setLikes(2);

        var resp = contentPostService.toResponse(post, 7L);

        assertThat(resp.portfolioCount()).isEqualTo(7L);
        verify(contentPostRepository, never()).save(any());
    }
}
