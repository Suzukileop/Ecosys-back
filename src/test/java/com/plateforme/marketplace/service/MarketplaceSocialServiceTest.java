package com.plateforme.marketplace.service;

import com.plateforme.marketplace.dto.CommentRequest;
import com.plateforme.marketplace.dto.ReactionCountsResponse;
import com.plateforme.marketplace.entity.*;
import com.plateforme.marketplace.repository.*;
import com.plateforme.shared.service.NotificationService;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketplaceSocialServiceTest {

    @Mock
    private ContentReactionRepository reactionRepository;
    @Mock
    private ContentFavoriteRepository favoriteRepository;
    @Mock
    private ContentCommentRepository commentRepository;
    @Mock
    private ContentReportRepository reportRepository;
    @Mock
    private ContentShareRepository shareRepository;
    @Mock
    private ContentPostRepository contentPostRepository;
    @Mock
    private MarketplaceProductRepository productRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private MarketplaceSocialService socialService;

    private UUID userId;
    private UUID targetId;
    private User user;
    private MarketplaceProduct product;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        targetId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        user.setFullName("Buyer");

        product = new MarketplaceProduct();
        product.setId(targetId);
        product.setLikes(5);
        product.setDislikes(1);
        User creator = new User();
        creator.setId(UUID.randomUUID());
        product.setCreator(creator);
    }

    @Test
    @DisplayName("addReaction on product increments likes")
    void addReaction_incrementsLikes() {
        when(productRepository.findByIdAndIsPublishedTrue(targetId)).thenReturn(Optional.of(product));
        when(productRepository.findById(targetId)).thenReturn(Optional.of(product));
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(reactionRepository.findByUser_IdAndTargetTypeAndTargetId(
                userId, ContentTargetType.PRODUCT, targetId)).thenReturn(Optional.empty());
        when(reactionRepository.save(any(ContentReaction.class))).thenAnswer(inv -> inv.getArgument(0));

        socialService.addReaction(userId, ContentTargetType.PRODUCT, targetId, ReactionType.LIKE);

        ArgumentCaptor<MarketplaceProduct> captor = ArgumentCaptor.forClass(MarketplaceProduct.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getLikes()).isEqualTo(6);
    }

    @Test
    @DisplayName("getReactionCounts returns like and dislike totals")
    void getReactionCounts_returnsTotals() {
        when(productRepository.findByIdAndIsPublishedTrue(targetId)).thenReturn(Optional.of(product));
        when(reactionRepository.countByTargetTypeAndTargetIdAndType(
                ContentTargetType.PRODUCT, targetId, ReactionType.LIKE)).thenReturn(10L);
        when(reactionRepository.countByTargetTypeAndTargetIdAndType(
                ContentTargetType.PRODUCT, targetId, ReactionType.DISLIKE)).thenReturn(2L);

        ReactionCountsResponse counts = socialService.getReactionCounts(ContentTargetType.PRODUCT, targetId, null);

        assertThat(counts.likes()).isEqualTo(10);
        assertThat(counts.dislikes()).isEqualTo(2);
        assertThat(counts.favorited()).isFalse();
        assertThat(counts.userReaction()).isNull();
    }

    @Test
    @DisplayName("addComment on post notifies creator")
    void addComment_notifiesCreator() {
        UUID creatorId = UUID.randomUUID();
        ContentPost post = new ContentPost();
        post.setId(targetId);
        User creator = new User();
        creator.setId(creatorId);
        post.setCreator(creator);

        when(contentPostRepository.findByIdAndIsPublicTrue(targetId)).thenReturn(Optional.of(post));
        when(contentPostRepository.findById(targetId)).thenReturn(Optional.of(post));
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(commentRepository.save(any(ContentComment.class))).thenAnswer(inv -> {
            ContentComment c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        CommentRequest request = new CommentRequest(
                ContentTargetType.POST, targetId, "Great post!", null);
        socialService.addComment(userId, request);

        verify(notificationService).createAndSend(
                eq(creatorId),
                eq("MARKETPLACE_NEW_COMMENT"),
                anyString(),
                anyString(),
                eq("PLATFORM"),
                eq(targetId),
                any(UUID.class));
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("submitReport notifies admins")
    void submitReport_notifiesAdmins() {
        when(productRepository.findByIdAndIsPublishedTrue(targetId)).thenReturn(Optional.of(product));
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(reportRepository.save(any(ContentReport.class))).thenAnswer(inv -> {
            ContentReport r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        var request = new com.plateforme.marketplace.dto.ReportRequest(
                ContentTargetType.PRODUCT, targetId, ReportReason.SPAM, "Spam content");
        socialService.submitReport(userId, request);

        verify(notificationService).sendBulkToRole(
                eq("ROLE_ADMIN"),
                eq("MARKETPLACE_REPORT"),
                anyString(),
                anyString(),
                any(UUID.class));
    }
}
