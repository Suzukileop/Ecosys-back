package com.plateforme.marketplace.service;

import com.plateforme.marketplace.dto.ProductReviewComposerStatusResponse;
import com.plateforme.marketplace.dto.ProductReviewRequest;
import com.plateforme.marketplace.dto.ProductReviewResponse;
import com.plateforme.marketplace.dto.ProductReviewSummaryResponse;
import com.plateforme.marketplace.entity.MarketplaceProduct;
import com.plateforme.marketplace.entity.MarketplaceProductReview;
import com.plateforme.marketplace.entity.MarketplaceProductReviewHelpfulVote;
import com.plateforme.marketplace.repository.MarketplaceProductRepository;
import com.plateforme.marketplace.repository.MarketplaceProductReviewHelpfulVoteRepository;
import com.plateforme.marketplace.repository.MarketplaceProductReviewRepository;
import com.plateforme.shared.dto.PagedResponse;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketplaceProductReviewService {

    public static final int DAILY_REVIEW_LIMIT = 3;

    private final MarketplaceProductReviewRepository reviewRepository;
    private final MarketplaceProductReviewHelpfulVoteRepository helpfulVoteRepository;
    private final MarketplaceProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PagedResponse<ProductReviewResponse> listReviews(UUID productId, UUID viewerUserId, Pageable pageable) {
        requirePublishedProduct(productId);
        Page<MarketplaceProductReview> page =
                reviewRepository.findByProduct_IdOrderByCreatedAtDesc(productId, pageable);
        Map<UUID, Boolean> viewerVotes = loadViewerVotes(viewerUserId, page.getContent());
        return PagedResponse.fromPage(page.map(review -> toResponse(review, viewerVotes.get(review.getId()))));
    }

    @Transactional(readOnly = true)
    public ProductReviewSummaryResponse getReviewSummary(UUID productId) {
        requirePublishedProduct(productId);

        long count = reviewRepository.countLatestReviewers(productId);
        Double average = reviewRepository.averageLatestRatingPerUser(productId);

        int rating5 = 0;
        int rating4 = 0;
        int rating3 = 0;
        int rating2 = 0;
        int rating1 = 0;

        for (Object[] row : reviewRepository.countLatestByRatingGrouped(productId)) {
            int rating = ((Number) row[0]).intValue();
            int rowCount = ((Number) row[1]).intValue();
            switch (rating) {
                case 5 -> rating5 = rowCount;
                case 4 -> rating4 = rowCount;
                case 3 -> rating3 = rowCount;
                case 2 -> rating2 = rowCount;
                case 1 -> rating1 = rowCount;
                default -> { }
            }
        }

        Double normalizedAverage = count == 0 || average == null
                ? null
                : BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP).doubleValue();

        return new ProductReviewSummaryResponse(normalizedAverage, (int) count, rating5, rating4, rating3, rating2, rating1);
    }

    @Transactional(readOnly = true)
    public ProductReviewComposerStatusResponse getMyReviewStatus(UUID userId, UUID productId) {
        requirePublishedProduct(productId);
        ProductReviewResponse latest = reviewRepository
                .findFirstByProduct_IdAndUser_IdOrderByCreatedAtDesc(productId, userId)
                .map(review -> toResponse(review, null))
                .orElse(null);
        int postedToday = countReviewsPostedToday(userId, productId);
        return new ProductReviewComposerStatusResponse(
                latest,
                postedToday,
                DAILY_REVIEW_LIMIT,
                postedToday < DAILY_REVIEW_LIMIT);
    }

    @Transactional
    public ProductReviewResponse submitReview(UUID userId, UUID productId, ProductReviewRequest request) {
        MarketplaceProduct product = requirePublishedProduct(productId);
        if (product.getCreator().getId().equals(userId)) {
            throw new BusinessException("REVIEW_NOT_ALLOWED", "Creators cannot review their own products.");
        }

        if (countReviewsPostedToday(userId, productId) >= DAILY_REVIEW_LIMIT) {
            throw new BusinessException(
                    "REVIEW_DAILY_LIMIT",
                    "You can post up to " + DAILY_REVIEW_LIMIT + " reviews per day for this product.");
        }

        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found."));

        MarketplaceProductReview review = new MarketplaceProductReview();
        review.setProduct(product);
        review.setUser(user);
        review.setRating(request.rating());
        review.setComment(request.comment() != null && !request.comment().isBlank()
                ? request.comment().trim()
                : null);

        review = reviewRepository.save(review);
        refreshProductRating(product);
        log.info("Product review created product={} user={} rating={}", productId, userId, request.rating());
        return toResponse(review, null);
    }

    @Transactional
    public void deleteReview(UUID userId, UUID reviewId) {
        MarketplaceProductReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException("REVIEW_NOT_FOUND", "Review not found."));
        if (!review.getUser().getId().equals(userId)) {
            throw new BusinessException("REVIEW_DELETE_NOT_ALLOWED", "You can only delete your own reviews.");
        }

        MarketplaceProduct product = review.getProduct();
        requirePublishedProduct(product.getId());
        reviewRepository.delete(review);
        refreshProductRating(product);
        log.info("Product review deleted product={} user={} review={}", product.getId(), userId, reviewId);
    }

    @Transactional
    public ProductReviewResponse voteHelpful(UUID userId, UUID reviewId, boolean helpful) {
        MarketplaceProductReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException("REVIEW_NOT_FOUND", "Review not found."));
        requirePublishedProduct(review.getProduct().getId());

        if (review.getUser().getId().equals(userId)) {
            throw new BusinessException("REVIEW_VOTE_NOT_ALLOWED", "You cannot vote on your own review.");
        }

        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found."));

        int yes = review.getHelpfulYesCount() != null ? review.getHelpfulYesCount() : 0;
        int no = review.getHelpfulNoCount() != null ? review.getHelpfulNoCount() : 0;

        var existing = helpfulVoteRepository.findByReview_IdAndUser_Id(reviewId, userId);

        if (existing.isPresent()) {
            MarketplaceProductReviewHelpfulVote vote = existing.get();
            if (vote.isHelpful() == helpful) {
                return toResponse(review, helpful);
            }
            if (vote.isHelpful()) {
                yes = Math.max(0, yes - 1);
                no++;
            } else {
                no = Math.max(0, no - 1);
                yes++;
            }
            vote.setHelpful(helpful);
            helpfulVoteRepository.save(vote);
        } else {
            MarketplaceProductReviewHelpfulVote vote = new MarketplaceProductReviewHelpfulVote();
            vote.setReview(review);
            vote.setUser(user);
            vote.setHelpful(helpful);
            helpfulVoteRepository.save(vote);
            if (helpful) {
                yes++;
            } else {
                no++;
            }
        }

        review.setHelpfulYesCount(yes);
        review.setHelpfulNoCount(no);
        review = reviewRepository.save(review);
        return toResponse(review, helpful);
    }

    private int countReviewsPostedToday(UUID userId, UUID productId) {
        LocalDateTime startOfDayUtc = LocalDate.now(ZoneOffset.UTC).atStartOfDay();
        return (int) reviewRepository.countByUser_IdAndProduct_IdAndCreatedAtGreaterThanEqual(
                userId, productId, startOfDayUtc);
    }

    private Map<UUID, Boolean> loadViewerVotes(UUID viewerUserId, List<MarketplaceProductReview> reviews) {
        if (viewerUserId == null || reviews.isEmpty()) {
            return Map.of();
        }
        List<UUID> reviewIds = reviews.stream().map(MarketplaceProductReview::getId).toList();
        return helpfulVoteRepository.findByUser_IdAndReview_IdIn(viewerUserId, reviewIds).stream()
                .collect(Collectors.toMap(v -> v.getReview().getId(), MarketplaceProductReviewHelpfulVote::isHelpful));
    }

    private MarketplaceProduct requirePublishedProduct(UUID productId) {
        return productRepository.findByIdAndIsPublishedTrue(productId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found."));
    }

    private void refreshProductRating(MarketplaceProduct product) {
        long count = reviewRepository.countLatestReviewers(product.getId());
        Double avg = reviewRepository.averageLatestRatingPerUser(product.getId());
        product.setReviewCount((int) count);
        if (avg == null || count == 0) {
            product.setAverageRating(null);
        } else {
            product.setAverageRating(
                    BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP).doubleValue());
        }
        productRepository.save(product);
    }

    private ProductReviewResponse toResponse(MarketplaceProductReview review, Boolean userHelpfulVote) {
        User user = review.getUser();
        return new ProductReviewResponse(
                review.getId(),
                user.getId(),
                user.getFullName(),
                user.getAvatarUrl(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt(),
                review.getHelpfulYesCount() != null ? review.getHelpfulYesCount() : 0,
                review.getHelpfulNoCount() != null ? review.getHelpfulNoCount() : 0,
                userHelpfulVote
        );
    }
}
