package com.plateforme.marketplace.service;

import com.plateforme.marketplace.dto.ProductReviewRequest;
import com.plateforme.marketplace.entity.MarketplaceProduct;
import com.plateforme.marketplace.entity.MarketplaceProductReview;
import com.plateforme.marketplace.repository.MarketplaceProductRepository;
import com.plateforme.marketplace.repository.MarketplaceProductReviewHelpfulVoteRepository;
import com.plateforme.marketplace.repository.MarketplaceProductReviewRepository;
import com.plateforme.shared.exception.BusinessException;
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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketplaceProductReviewServiceTest {

    @Mock
    private MarketplaceProductReviewRepository reviewRepository;
    @Mock
    private MarketplaceProductReviewHelpfulVoteRepository helpfulVoteRepository;
    @Mock
    private MarketplaceProductRepository productRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MarketplaceProductReviewService reviewService;

    private UUID userId;
    private UUID productId;
    private User user;
    private User creator;
    private MarketplaceProduct product;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        productId = UUID.randomUUID();

        user = new User();
        user.setId(userId);
        user.setFullName("Buyer");

        creator = new User();
        creator.setId(UUID.randomUUID());

        product = new MarketplaceProduct();
        product.setId(productId);
        product.setCreator(creator);
    }

    @Test
    @DisplayName("submitReview creates a new row when under daily limit")
    void submitReview_createsNewReview() {
        when(productRepository.findByIdAndIsPublishedTrue(productId)).thenReturn(Optional.of(product));
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(reviewRepository.countByUser_IdAndProduct_IdAndCreatedAtGreaterThanEqual(
                eq(userId), eq(productId), any(LocalDateTime.class))).thenReturn(1L);
        when(reviewRepository.save(any(MarketplaceProductReview.class))).thenAnswer(inv -> {
            MarketplaceProductReview saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(LocalDateTime.now());
            return saved;
        });
        when(reviewRepository.countLatestReviewers(productId)).thenReturn(1L);
        when(reviewRepository.averageLatestRatingPerUser(productId)).thenReturn(4.0);

        var response = reviewService.submitReview(
                userId, productId, new ProductReviewRequest(4, "Great product"));

        assertThat(response.rating()).isEqualTo(4);
        verify(reviewRepository).save(any(MarketplaceProductReview.class));
        verify(productRepository).save(any(MarketplaceProduct.class));
    }

    @Test
    @DisplayName("submitReview rejects when daily limit reached")
    void submitReview_rejectsDailyLimit() {
        when(productRepository.findByIdAndIsPublishedTrue(productId)).thenReturn(Optional.of(product));
        when(reviewRepository.countByUser_IdAndProduct_IdAndCreatedAtGreaterThanEqual(
                eq(userId), eq(productId), any(LocalDateTime.class))).thenReturn(3L);

        assertThatThrownBy(() -> reviewService.submitReview(
                userId, productId, new ProductReviewRequest(5, "Another one")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "REVIEW_DAILY_LIMIT");

        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteReview removes own review and refreshes product rating")
    void deleteReview_removesOwnReview() {
        UUID reviewId = UUID.randomUUID();
        MarketplaceProductReview review = new MarketplaceProductReview();
        review.setId(reviewId);
        review.setProduct(product);
        review.setUser(user);
        review.setRating(3);

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(productRepository.findByIdAndIsPublishedTrue(productId)).thenReturn(Optional.of(product));
        when(reviewRepository.countLatestReviewers(productId)).thenReturn(0L);
        when(reviewRepository.averageLatestRatingPerUser(productId)).thenReturn(null);

        reviewService.deleteReview(userId, reviewId);

        verify(reviewRepository).delete(review);
        ArgumentCaptor<MarketplaceProduct> captor = ArgumentCaptor.forClass(MarketplaceProduct.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getReviewCount()).isZero();
        assertThat(captor.getValue().getAverageRating()).isNull();
    }
}
