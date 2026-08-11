package com.plateforme.user.repository;

import com.plateforme.user.entity.CreatorPortfolioPost;
import com.plateforme.user.entity.CreatorPortfolioPostId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CreatorPortfolioPostRepository extends JpaRepository<CreatorPortfolioPost, CreatorPortfolioPostId> {

    List<CreatorPortfolioPost> findByCreator_IdOrderBySortOrderAsc(UUID creatorUserId);

    @Query("""
            SELECT cpp FROM CreatorPortfolioPost cpp
            JOIN FETCH cpp.contentPost cp
            WHERE cpp.creator.id = :creatorUserId
            AND cp.deletedAt IS NULL
            AND cp.archivedAt IS NULL
            ORDER BY cpp.sortOrder ASC
            """)
    List<CreatorPortfolioPost> findActiveCuratedByCreatorId(@Param("creatorUserId") UUID creatorUserId);

    @Query("""
            SELECT cpp FROM CreatorPortfolioPost cpp
            JOIN FETCH cpp.contentPost cp
            WHERE cpp.creator.id = :creatorUserId
            AND cp.isPublic = true
            AND cp.deletedAt IS NULL
            AND cp.archivedAt IS NULL
            ORDER BY cpp.sortOrder ASC
            """)
    List<CreatorPortfolioPost> findPublicCuratedByCreatorId(@Param("creatorUserId") UUID creatorUserId);

    @Query("""
            SELECT COUNT(cpp) FROM CreatorPortfolioPost cpp
            JOIN cpp.contentPost cp
            WHERE cpp.creator.id = :creatorUserId
            AND cp.isPublic = true
            AND cp.deletedAt IS NULL
            AND cp.archivedAt IS NULL
            """)
    long countPublicCuratedByCreatorId(@Param("creatorUserId") UUID creatorUserId);

    @Modifying
    @Query("DELETE FROM CreatorPortfolioPost cpp WHERE cpp.creator.id = :creatorUserId")
    void deleteAllByCreatorUserId(@Param("creatorUserId") UUID creatorUserId);
}
