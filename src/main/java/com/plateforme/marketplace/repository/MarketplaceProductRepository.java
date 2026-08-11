package com.plateforme.marketplace.repository;

import com.plateforme.marketplace.entity.ContentTargetType;
import com.plateforme.marketplace.entity.MarketplaceProduct;
import com.plateforme.marketplace.entity.ProductType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MarketplaceProductRepository extends JpaRepository<MarketplaceProduct, UUID> {

    Page<MarketplaceProduct> findByCreator_IdOrderByCreatedAtDesc(UUID creatorId, Pageable pageable);

    long countByCreator_IdAndIsPublishedTrue(UUID creatorId);

    Optional<MarketplaceProduct> findByIdAndIsPublishedTrue(UUID id);

    @Query("""
            SELECT p FROM MarketplaceProduct p
            WHERE p.isPublished = true
            AND (:creatorId IS NULL OR p.creator.id = :creatorId)
            AND (:type IS NULL OR p.type = :type)
            AND (:genre IS NULL OR p.genre = :genre)
            AND (:q IS NULL OR :q = ''
                OR LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(CAST(p.tags AS string)) LIKE LOWER(CONCAT('%', :q, '%')))
            AND (:freeOnly = true AND p.priceCents = 0
                OR :freeOnly = false
                    AND (:minPriceCents IS NULL OR p.priceCents >= :minPriceCents)
                    AND (:maxPriceCents IS NULL OR p.priceCents <= :maxPriceCents))
            AND (:userId IS NULL OR EXISTS (
                SELECT 1 FROM ContentFavorite f
                WHERE f.targetId = p.id
                AND f.targetType = :favoriteTargetType
                AND f.user.id = :userId
            ))
            """)
    Page<MarketplaceProduct> findPublishedFiltered(
            @Param("creatorId") UUID creatorId,
            @Param("type") ProductType type,
            @Param("genre") String genre,
            @Param("q") String q,
            @Param("freeOnly") boolean freeOnly,
            @Param("minPriceCents") Integer minPriceCents,
            @Param("maxPriceCents") Integer maxPriceCents,
            @Param("userId") UUID userId,
            @Param("favoriteTargetType") ContentTargetType favoriteTargetType,
            Pageable pageable);
}
