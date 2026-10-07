package com.plateforme.marketplace.repository;

import com.plateforme.marketplace.entity.MarketplaceProductGroupItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface MarketplaceProductGroupItemRepository extends JpaRepository<MarketplaceProductGroupItem, UUID> {

    List<MarketplaceProductGroupItem> findByProductGroup_IdAndProduct_DeletedAtIsNullOrderBySortOrderAsc(UUID groupId);

    List<MarketplaceProductGroupItem> findByProductGroup_IdAndProduct_DeletedAtIsNullAndProduct_IsPublishedTrueOrderBySortOrderAsc(
            UUID groupId);

    void deleteByProductGroup_Id(UUID groupId);

    void deleteByProduct_Id(UUID productId);

    /** Rows of {@code [catalogueId, catalogueName, productId, salesCount]} for published products with sales. */
    @Query("""
            SELECT g.id, g.name, p.id, p.salesCount
            FROM MarketplaceProductGroupItem i
            JOIN i.productGroup g
            JOIN i.product p
            WHERE g.creator.id IN :creatorIds
            AND g.deletedAt IS NULL
            AND p.isPublished = true
            AND p.deletedAt IS NULL
            AND p.salesCount > 0
            """)
    List<Object[]> findSellingItemsByCreatorIds(@Param("creatorIds") Collection<UUID> creatorIds);
}
