package com.plateforme.user.service;

import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.dto.CreatorReputationDto;
import com.plateforme.user.dto.CreatorReviewItemDto;
import com.plateforme.user.dto.SubmitCreatorReviewDto;
import com.plateforme.user.entity.CreatorProfile;
import com.plateforme.user.entity.CreatorReview;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.CreatorProfileRepository;
import com.plateforme.user.repository.CreatorReviewRepository;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreatorReviewService {

    private final CreatorReviewRepository creatorReviewRepository;
    private final CreatorProfileRepository creatorProfileRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public CreatorReputationDto getReputation(UUID creatorUserId, int recentLimit) {
        CreatorProfile profile = creatorProfileRepository.findByUserId(creatorUserId).orElse(null);
        boolean verified = profile != null && Boolean.TRUE.equals(profile.getIsVerified());

        long reviewCount = creatorReviewRepository.countByCreator_Id(creatorUserId);
        if (reviewCount == 0) {
            return new CreatorReputationDto(null, 0, 0, buildTrustBadges(verified, null, 0, 0), List.of(),
                    getRatingDistribution(creatorUserId));
        }

        Double avg = creatorReviewRepository.averageRating(creatorUserId);
        long recommendCount = creatorReviewRepository.countByCreator_IdAndWouldRecommendTrue(creatorUserId);
        int recommendPercent = (int) Math.round((recommendCount * 100.0) / reviewCount);

        List<CreatorReviewItemDto> recent = recentLimit > 0
                ? creatorReviewRepository
                        .findByCreator_IdOrderByCreatedAtDesc(creatorUserId, PageRequest.of(0, recentLimit))
                        .stream()
                        .map(this::toItem)
                        .toList()
                : List.of();

        return new CreatorReputationDto(
                avg != null ? Math.round(avg * 10.0) / 10.0 : null,
                reviewCount,
                recommendPercent,
                buildTrustBadges(verified, avg, reviewCount, recommendPercent),
                recent,
                getRatingDistribution(creatorUserId)
        );
    }

    @Transactional(readOnly = true)
    public Map<Integer, Integer> getRatingDistribution(UUID creatorUserId) {
        Map<Integer, Integer> distribution = new LinkedHashMap<>();
        for (int star = 1; star <= 5; star++) {
            distribution.put(star, 0);
        }
        for (Object[] row : creatorReviewRepository.countByRatingGrouped(creatorUserId)) {
            int rating = ((Number) row[0]).intValue();
            int count = ((Number) row[1]).intValue();
            if (rating >= 1 && rating <= 5) {
                distribution.put(rating, count);
            }
        }
        return distribution;
    }

    @Transactional
    public CreatorReviewItemDto submitReview(UUID creatorUserId, UUID reviewerId, SubmitCreatorReviewDto dto) {
        if (creatorUserId.equals(reviewerId)) {
            throw new BusinessException("REVIEW_NOT_ALLOWED", "You cannot review your own profile.");
        }

        User creator = userRepository.findByIdAndDeletedAtIsNull(creatorUserId)
                .orElseThrow(() -> new BusinessException("CREATOR_NOT_FOUND", "Creator not found."));
        boolean isCreator = creator.getRoles().stream().anyMatch(r -> "ROLE_CREATOR".equals(r.getName()));
        if (!isCreator) {
            throw new BusinessException("CREATOR_NOT_FOUND", "Creator not found.");
        }

        User reviewer = userRepository.findByIdAndDeletedAtIsNull(reviewerId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Reviewer not found."));

        if (creatorReviewRepository.existsByCreator_IdAndReviewer_Id(creatorUserId, reviewerId)) {
            throw new BusinessException("REVIEW_ALREADY_EXISTS", "You have already reviewed this creator.");
        }

        CreatorReview review = new CreatorReview();
        review.setCreator(creator);
        review.setReviewer(reviewer);
        review.setRating(dto.rating());
        review.setComment(dto.comment() != null ? dto.comment().trim() : null);
        review.setWouldRecommend(Boolean.TRUE.equals(dto.wouldRecommend()));
        review = creatorReviewRepository.save(review);
        return toItem(review);
    }

    private CreatorReviewItemDto toItem(CreatorReview review) {
        return new CreatorReviewItemDto(
                review.getId(),
                review.getReviewer().getFullName(),
                review.getRating(),
                review.getComment(),
                Boolean.TRUE.equals(review.getWouldRecommend()),
                review.getCreatedAt()
        );
    }

    private List<String> buildTrustBadges(boolean verified, Double avg, long reviewCount, int recommendPercent) {
        List<String> badges = new ArrayList<>();
        if (verified) {
            badges.add("Verified creator");
        }
        if (avg != null && avg >= 4.5 && reviewCount >= 5) {
            badges.add("Highly rated");
        }
        if (recommendPercent >= 90 && reviewCount >= 10) {
            badges.add("Community favorite");
        }
        if (reviewCount >= 3 && avg != null && avg >= 4.0) {
            badges.add("Trusted by clients");
        }
        return badges;
    }
}
