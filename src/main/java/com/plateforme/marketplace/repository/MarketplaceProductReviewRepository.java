package com.plateforme.marketplace.repository;

import com.plateforme.marketplace.entity.MarketplaceProductReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MarketplaceProductReviewRepository extends JpaRepository<MarketplaceProductReview, UUID> {

    Page<MarketplaceProductReview> findByProduct_IdOrderByCreatedAtDesc(UUID productId, Pageable pageable);

    Optional<MarketplaceProductReview> findFirstByProduct_IdAndUser_IdOrderByCreatedAtDesc(
            UUID productId, UUID userId);

    long countByProduct_Id(UUID productId);

    long countByUser_IdAndProduct_IdAndCreatedAtGreaterThanEqual(
            UUID userId, UUID productId, LocalDateTime createdAt);

    @Query(value = """
            WITH latest AS (
                SELECT DISTINCT ON (user_id) rating
                FROM marketplace_product_reviews
                WHERE product_id = :productId
                ORDER BY user_id, created_at DESC
            )
            SELECT AVG(rating) FROM latest
            """, nativeQuery = true)
    Double averageLatestRatingPerUser(@Param("productId") UUID productId);

    @Query(value = """
            WITH latest AS (
                SELECT DISTINCT ON (user_id) rating
                FROM marketplace_product_reviews
                WHERE product_id = :productId
                ORDER BY user_id, created_at DESC
            )
            SELECT COUNT(*) FROM latest
            """, nativeQuery = true)
    long countLatestReviewers(@Param("productId") UUID productId);

    @Query(value = """
            WITH latest AS (
                SELECT DISTINCT ON (user_id) rating
                FROM marketplace_product_reviews
                WHERE product_id = :productId
                ORDER BY user_id, created_at DESC
            )
            SELECT rating, COUNT(*) FROM latest GROUP BY rating
            """, nativeQuery = true)
    List<Object[]> countLatestByRatingGrouped(@Param("productId") UUID productId);
}
