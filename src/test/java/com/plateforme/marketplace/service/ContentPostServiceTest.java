package com.plateforme.marketplace.service;

import com.plateforme.marketplace.dto.ContentPostRequest;
import com.plateforme.marketplace.entity.ContentPost;
import com.plateforme.marketplace.repository.ContentPostHideRepository;
import com.plateforme.marketplace.repository.ContentPostRepository;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.CreatorProfileRepository;
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
    private ContentPostHideRepository contentPostHideRepository;

    @Mock
    private com.plateforme.user.service.FollowerPublishNotifyService followerPublishNotifyService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CreatorProfileRepository creatorProfileRepository;

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
                "t", "g", "https://m.com/x", null, null, null, null, null, null, null, null, tools, null, true, true);

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

        when(contentPostRepository.findPublicById(postId)).thenReturn(Optional.of(post));

        contentPostService.incrementView(postId);

        assertThat(post.getViews()).isEqualTo(4);
        verify(contentPostRepository).save(post);
    }

    @Test
    @DisplayName("getPublicPostById : contenu non public → BusinessException")
    void getPublicPostById_notFound() {
        UUID postId = UUID.randomUUID();
        when(contentPostRepository.findPublicById(postId)).thenReturn(Optional.empty());

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

        when(creatorProfileRepository.findByUserId(creatorId)).thenReturn(Optional.empty());

        var resp = contentPostService.toResponse(post, 7L);

        assertThat(resp.portfolioCount()).isEqualTo(7L);
        verify(contentPostRepository, never()).save(any());
    }

    private ContentPost publicPostBy(User author) {
        ContentPost post = new ContentPost();
        post.setId(UUID.randomUUID());
        post.setCreator(author);
        post.setIsPublic(true);
        post.setTitle("original");
        return post;
    }

    private User otherUser() {
        User other = new User();
        other.setId(UUID.randomUUID());
        other.setFullName("Other");
        other.setEmail("o@test.com");
        other.setPasswordHash("x");
        return other;
    }

    @Test
    @DisplayName("createPost : gallery of 3 images -> cover is the first image, gallery stored")
    void createPost_gallerySetsCoverAndStoresList() {
        when(userRepository.findByIdAndDeletedAtIsNull(creatorId)).thenReturn(Optional.of(creator));
        when(contentPostRepository.save(any(ContentPost.class))).thenAnswer(inv -> inv.getArgument(0));
        when(creatorProfileRepository.findByUserId(creatorId)).thenReturn(Optional.empty());

        List<String> urls = List.of(
                "https://m.com/a.jpg", " https://m.com/b.jpg ", "https://m.com/c.jpg", "https://m.com/a.jpg");
        ContentPostRequest req = new ContentPostRequest(
                null, null, "https://m.com/ignored.jpg", "GIF", urls,
                null, null, null, null, null, null, null, null, true, true);

        var resp = contentPostService.createPost(creatorId, req);

        assertThat(resp.mediaUrl()).isEqualTo("https://m.com/a.jpg");
        assertThat(resp.mediaUrls())
                .containsExactly("https://m.com/a.jpg", "https://m.com/b.jpg", "https://m.com/c.jpg");
        assertThat(resp.mediaType()).isEqualTo("FILE");
    }

    @Test
    @DisplayName("createPost : more than 10 images -> MEDIA_URLS_LIMIT")
    void createPost_galleryLimit() {
        when(userRepository.findByIdAndDeletedAtIsNull(creatorId)).thenReturn(Optional.of(creator));
        List<String> urls = java.util.stream.IntStream.range(0, 11)
                .mapToObj(i -> "https://m.com/" + i + ".jpg").toList();
        ContentPostRequest req = new ContentPostRequest(
                null, null, null, null, urls, null, null, null, null, null, null, null, null, true, true);

        assertThatThrownBy(() -> contentPostService.createPost(creatorId, req))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("MEDIA_URLS_LIMIT");
    }

    @Test
    @DisplayName("createPost : invalid image address -> INVALID_MEDIA_URL")
    void createPost_galleryRejectsNonUrl() {
        when(userRepository.findByIdAndDeletedAtIsNull(creatorId)).thenReturn(Optional.of(creator));
        ContentPostRequest req = new ContentPostRequest(
                null, null, null, null, List.of("javascript:alert(1)"),
                null, null, null, null, null, null, null, null, true, true);

        assertThatThrownBy(() -> contentPostService.createPost(creatorId, req))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_MEDIA_URL");
    }

    @Test
    @DisplayName("repostPost : reposting a public post creates a post pointing at the original")
    void repostPost_createsPostPointingAtOriginal() {
        ContentPost original = publicPostBy(otherUser());
        when(userRepository.findByIdAndDeletedAtIsNull(creatorId)).thenReturn(Optional.of(creator));
        when(contentPostRepository.findPublicById(original.getId())).thenReturn(Optional.of(original));
        when(contentPostRepository.findFirstByCreator_IdAndRepostOfId(creatorId, original.getId()))
                .thenReturn(Optional.empty());
        when(contentPostRepository.save(any(ContentPost.class))).thenAnswer(inv -> inv.getArgument(0));
        when(contentPostRepository.findById(original.getId())).thenReturn(Optional.of(original));
        when(creatorProfileRepository.findByUserId(any())).thenReturn(Optional.empty());

        var resp = contentPostService.repostPost(creatorId, original.getId(), "  great work  ");

        assertThat(resp.title()).isEqualTo("great work");
        assertThat(resp.repostOf()).isNotNull();
        assertThat(resp.repostOf().id()).isEqualTo(original.getId());
        assertThat(resp.mediaUrls()).isEmpty();
    }

    @Test
    @DisplayName("repostPost : reposting a repost reposts the original (no chains)")
    void repostPost_flattensChains() {
        ContentPost original = publicPostBy(otherUser());
        ContentPost viaSomeoneElse = publicPostBy(otherUser());
        viaSomeoneElse.setRepostOfId(original.getId());
        when(userRepository.findByIdAndDeletedAtIsNull(creatorId)).thenReturn(Optional.of(creator));
        when(contentPostRepository.findPublicById(viaSomeoneElse.getId())).thenReturn(Optional.of(viaSomeoneElse));
        when(contentPostRepository.findPublicById(original.getId())).thenReturn(Optional.of(original));
        when(contentPostRepository.findFirstByCreator_IdAndRepostOfId(creatorId, original.getId()))
                .thenReturn(Optional.empty());
        when(contentPostRepository.save(any(ContentPost.class))).thenAnswer(inv -> inv.getArgument(0));
        when(contentPostRepository.findById(original.getId())).thenReturn(Optional.of(original));
        when(creatorProfileRepository.findByUserId(any())).thenReturn(Optional.empty());

        var resp = contentPostService.repostPost(creatorId, viaSomeoneElse.getId(), null);

        assertThat(resp.repostOf().id()).isEqualTo(original.getId());
    }

    @Test
    @DisplayName("repostPost : own post -> REPOST_OWN_POST")
    void repostPost_rejectsOwnPost() {
        ContentPost own = publicPostBy(creator);
        when(userRepository.findByIdAndDeletedAtIsNull(creatorId)).thenReturn(Optional.of(creator));
        when(contentPostRepository.findPublicById(own.getId())).thenReturn(Optional.of(own));

        assertThatThrownBy(() -> contentPostService.repostPost(creatorId, own.getId(), null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("REPOST_OWN_POST");
        verify(contentPostRepository, never()).save(any());
    }

    @Test
    @DisplayName("repostPost : already reposted -> ALREADY_REPOSTED")
    void repostPost_rejectsDuplicate() {
        ContentPost original = publicPostBy(otherUser());
        when(userRepository.findByIdAndDeletedAtIsNull(creatorId)).thenReturn(Optional.of(creator));
        when(contentPostRepository.findPublicById(original.getId())).thenReturn(Optional.of(original));
        when(contentPostRepository.findFirstByCreator_IdAndRepostOfId(creatorId, original.getId()))
                .thenReturn(Optional.of(new ContentPost()));

        assertThatThrownBy(() -> contentPostService.repostPost(creatorId, original.getId(), null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("ALREADY_REPOSTED");
    }

    @Test
    @DisplayName("undoRepost : soft deletes my repost")
    void undoRepost_softDeletes() {
        UUID originalId = UUID.randomUUID();
        ContentPost mine = new ContentPost();
        mine.setId(UUID.randomUUID());
        mine.setCreator(creator);
        mine.setRepostOfId(originalId);
        when(contentPostRepository.findById(originalId)).thenReturn(Optional.empty());
        when(contentPostRepository.findFirstByCreator_IdAndRepostOfId(creatorId, originalId))
                .thenReturn(Optional.of(mine));

        contentPostService.undoRepost(creatorId, originalId);

        assertThat(mine.getDeletedAt()).isNotNull();
        verify(contentPostRepository).save(mine);
    }

    @Test
    @DisplayName("hidePost : hides someone else's post, refuses your own")
    void hidePost_hidesOthersRejectsOwn() {
        ContentPost others = publicPostBy(otherUser());
        when(contentPostRepository.findPublicById(others.getId())).thenReturn(Optional.of(others));
        when(contentPostHideRepository.existsByUserIdAndPostId(creatorId, others.getId())).thenReturn(false);

        contentPostService.hidePost(creatorId, others.getId());
        verify(contentPostHideRepository).save(any());

        ContentPost own = publicPostBy(creator);
        when(contentPostRepository.findPublicById(own.getId())).thenReturn(Optional.of(own));
        assertThatThrownBy(() -> contentPostService.hidePost(creatorId, own.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("HIDE_OWN_POST");
    }
}
