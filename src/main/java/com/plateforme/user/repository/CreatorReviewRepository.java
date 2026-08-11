package com.plateforme.user.repository;

import com.plateforme.user.entity.CreatorReview;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CreatorReviewRepository extends JpaRepository<CreatorReview, UUID> {

    @Query("SELECT AVG(r.rating) FROM CreatorReview r WHERE r.creator.id = :creatorId")
    Double averageRating(@Param("creatorId") UUID creatorId);

    long countByCreator_Id(UUID creatorId);

    long countByCreator_IdAndWouldRecommendTrue(UUID creatorId);

    List<CreatorReview> findByCreator_IdOrderByCreatedAtDesc(UUID creatorId, Pageable pageable);

    @Query("""
            SELECT r.rating, COUNT(r)
            FROM CreatorReview r
            WHERE r.creator.id = :creatorId
            GROUP BY r.rating
            """)
    List<Object[]> countByRatingGrouped(@Param("creatorId") UUID creatorId);

    boolean existsByCreator_IdAndReviewer_Id(UUID creatorId, UUID reviewerId);
}
