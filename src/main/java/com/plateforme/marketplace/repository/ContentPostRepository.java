package com.plateforme.marketplace.repository;

import com.plateforme.marketplace.entity.ContentPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContentPostRepository extends JpaRepository<ContentPost, UUID> {

    @Query("""
            SELECT cp FROM ContentPost cp
            WHERE cp.creator.id = :creatorId
            AND cp.archivedAt IS NULL
            ORDER BY cp.pinnedAt DESC NULLS LAST, cp.createdAt DESC
            """)
    Page<ContentPost> findActiveByCreatorId(@Param("creatorId") UUID creatorId, Pageable pageable);

    @Query("""
            SELECT cp FROM ContentPost cp
            WHERE cp.creator.id = :creatorId
            AND cp.archivedAt IS NULL
            AND cp.pinnedAt IS NOT NULL
            ORDER BY cp.pinnedAt DESC, cp.createdAt DESC
            """)
    Page<ContentPost> findPinnedByCreatorId(@Param("creatorId") UUID creatorId, Pageable pageable);

    @Query("""
            SELECT cp FROM ContentPost cp
            WHERE cp.creator.id = :creatorId
            AND cp.archivedAt IS NOT NULL
            ORDER BY cp.archivedAt DESC
            """)
    Page<ContentPost> findArchivedByCreatorId(@Param("creatorId") UUID creatorId, Pageable pageable);

    @Query(value = """
            SELECT * FROM content_posts cp
            WHERE cp.creator_id = :creatorId
            AND cp.deleted_at IS NOT NULL
            ORDER BY cp.deleted_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM content_posts cp
            WHERE cp.creator_id = :creatorId
            AND cp.deleted_at IS NOT NULL
            """,
            nativeQuery = true)
    Page<ContentPost> findTrashByCreatorId(@Param("creatorId") UUID creatorId, Pageable pageable);

    Page<ContentPost> findByCreator_IdOrderByCreatedAtDesc(UUID creatorId, Pageable pageable);

    @Query("""
            SELECT cp FROM ContentPost cp
            WHERE cp.creator.id = :creatorId
            AND cp.isPublic = true
            AND cp.archivedAt IS NULL
            ORDER BY cp.pinnedAt DESC NULLS LAST, cp.createdAt DESC
            """)
    Page<ContentPost> findByCreator_IdAndIsPublicTrueOrderByCreatedAtDesc(UUID creatorId, Pageable pageable);

    Optional<ContentPost> findByIdAndIsPublicTrue(UUID id);

    @Query("""
            SELECT cp FROM ContentPost cp
            WHERE cp.id = :id
            AND cp.isPublic = true
            AND cp.archivedAt IS NULL
            """)
    Optional<ContentPost> findPublicById(@Param("id") UUID id);

    @Query("""
            SELECT COUNT(cp) FROM ContentPost cp
            WHERE cp.creator.id = :creatorId
            AND cp.archivedAt IS NULL
            """)
    long countActiveByCreator_Id(@Param("creatorId") UUID creatorId);

    long countByCreator_Id(UUID creatorId);

    long countByCreator_IdAndIsPublicTrue(UUID creatorId);

    @Query("""
            SELECT cp FROM ContentPost cp
            WHERE cp.isPublic = true
            AND cp.archivedAt IS NULL
            AND (:creatorId IS NULL OR cp.creator.id = :creatorId)
            AND (:genre IS NULL OR cp.genre = :genre)
            AND (:q IS NULL OR :q = ''
                OR LOWER(COALESCE(cp.title, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(cp.genre, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(cp.description, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(cp.moodLabel, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(CAST(cp.tags AS string)) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY cp.pinnedAt DESC NULLS LAST, cp.createdAt DESC
            """)
    Page<ContentPost> findPublicFiltered(
            @Param("creatorId") UUID creatorId,
            @Param("genre") String genre,
            @Param("q") String q,
            Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(cp.views), 0) FROM ContentPost cp
            WHERE cp.creator.id = :creatorId
            AND cp.archivedAt IS NULL
            """)
    Long sumViewsByCreatorId(@Param("creatorId") UUID creatorId);

    @Query("""
            SELECT COALESCE(SUM(cp.likes), 0) FROM ContentPost cp
            WHERE cp.creator.id = :creatorId
            AND cp.archivedAt IS NULL
            """)
    Long sumLikesByCreatorId(@Param("creatorId") UUID creatorId);

    @Modifying
    @Query(value = """
            UPDATE content_posts
            SET deleted_at = NULL, updated_at = NOW()
            WHERE id = :postId AND creator_id = :creatorId AND deleted_at IS NOT NULL
            """, nativeQuery = true)
    int restoreFromTrash(@Param("creatorId") UUID creatorId, @Param("postId") UUID postId);

    @Modifying
    @Query(value = """
            DELETE FROM content_posts
            WHERE id = :postId AND creator_id = :creatorId AND deleted_at IS NOT NULL
            """, nativeQuery = true)
    int permanentDelete(@Param("creatorId") UUID creatorId, @Param("postId") UUID postId);

    @Query(value = """
            SELECT * FROM content_posts
            WHERE id = :postId AND creator_id = :creatorId AND deleted_at IS NOT NULL
            """, nativeQuery = true)
    Optional<ContentPost> findTrashById(@Param("creatorId") UUID creatorId, @Param("postId") UUID postId);
}
