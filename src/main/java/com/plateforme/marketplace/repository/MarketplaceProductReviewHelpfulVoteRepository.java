package com.plateforme.marketplace.repository;

import com.plateforme.marketplace.entity.MarketplaceProductReviewHelpfulVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MarketplaceProductReviewHelpfulVoteRepository
        extends JpaRepository<MarketplaceProductReviewHelpfulVote, UUID> {

    Optional<MarketplaceProductReviewHelpfulVote> findByReview_IdAndUser_Id(UUID reviewId, UUID userId);

    @Query("""
            SELECT v FROM MarketplaceProductReviewHelpfulVote v
            WHERE v.user.id = :userId AND v.review.id IN :reviewIds
            """)
    List<MarketplaceProductReviewHelpfulVote> findByUser_IdAndReview_IdIn(
            @Param("userId") UUID userId,
            @Param("reviewIds") Collection<UUID> reviewIds);
}
