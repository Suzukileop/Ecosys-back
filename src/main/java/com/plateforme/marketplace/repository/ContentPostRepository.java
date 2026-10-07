package com.plateforme.marketplace.repository;

import com.plateforme.marketplace.entity.ContentPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
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

    List<ContentPost> findByCreator_IdOrderByCreatedAtDesc(UUID creatorId);

    /**
     * Public feed search — same keyword pipeline as the creator search ({@code CreatorSearchExpand}):
     * the raw query, its canonical specialty and expanded synonyms/tools are matched against the post
     * and its author's profile (specialties, tags, tools, bio). 2–3 letter alphanumeric terms
     * ("ai", "ui", "3d") match whole words only, so "ai" does not hit "email" or "paint".
     * :q / :qCanonical / :terms are '' when unused.
     */
    @Query(value = """
            SELECT cp.* FROM content_posts cp
            INNER JOIN users u ON u.id = cp.creator_id
            LEFT JOIN creator_profiles prof ON prof.user_id = cp.creator_id
            CROSS JOIN LATERAL (
                SELECT
                    LOWER(COALESCE(cp.title, '')) AS title,
                    LOWER(COALESCE(cp.genre, '')) AS genre,
                    LOWER(COALESCE(cp.tags::text, '')) AS tags,
                    LOWER(COALESCE(cp.tools_used::text, '')) AS tools,
                    LOWER(CONCAT_WS(' ', cp.description, cp.mood_label, cp.price_info)) AS body,
                    LOWER(CONCAT_WS(' ', prof.specialite, prof.specialties::text, prof.specialty_tags::text,
                        prof.strengths_tools_mastered::text, prof.profile_services::text)) AS skills,
                    LOWER(CONCAT_WS(' ', u.full_name, prof.shop_name, prof.bio)) AS author
            ) s
            WHERE cp.deleted_at IS NULL
            AND u.deleted_at IS NULL
            AND cp.is_public = true
            AND cp.archived_at IS NULL
            AND (CAST(:creatorId AS UUID) IS NULL OR cp.creator_id = CAST(:creatorId AS UUID))
            AND (CAST(:genre AS VARCHAR) IS NULL OR cp.genre = CAST(:genre AS VARCHAR))
            AND (cp.repost_of_id IS NULL OR EXISTS (
                SELECT 1 FROM content_posts orig
                WHERE orig.id = cp.repost_of_id
                AND orig.deleted_at IS NULL AND orig.is_public = true AND orig.archived_at IS NULL
            ))
            AND (CAST(:viewerId AS UUID) IS NULL OR CAST(:creatorId AS UUID) IS NOT NULL OR NOT EXISTS (
                SELECT 1 FROM content_post_hides h
                WHERE h.user_id = CAST(:viewerId AS UUID) AND h.post_id = cp.id
            ))
            AND (
                CAST(:terms AS VARCHAR) = ''
                OR EXISTS (
                    SELECT 1 FROM unnest(string_to_array(CAST(:terms AS VARCHAR), '|')) AS x(term)
                    WHERE length(btrim(term)) >= 2
                    AND (
                        CASE
                          WHEN LOWER(btrim(term)) ~ '^[a-z0-9]{2,3}$'
                          THEN CONCAT_WS(' ', s.title, s.genre, s.tags, s.tools, s.body, s.skills, s.author)
                               ~ ('\\m' || LOWER(btrim(term)) || '\\M')
                          ELSE CONCAT_WS(' ', s.title, s.genre, s.tags, s.tools, s.body, s.skills, s.author)
                               LIKE CONCAT('%', LOWER(btrim(term)), '%')
                        END
                    )
                )
            )
            ORDER BY (
                CASE
                  WHEN CAST(:q AS VARCHAR) = '' THEN 0
                  WHEN s.genre = LOWER(TRIM(CAST(:q AS VARCHAR)))
                    OR EXISTS (
                        SELECT 1 FROM jsonb_array_elements_text(COALESCE(cp.tags, '[]'::jsonb)) tag
                        WHERE LOWER(TRIM(tag)) = LOWER(TRIM(CAST(:q AS VARCHAR)))
                           OR (CAST(:qCanonical AS VARCHAR) <> '' AND LOWER(TRIM(tag)) = LOWER(CAST(:qCanonical AS VARCHAR)))
                    )
                  THEN 100
                  WHEN s.title LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%')) THEN 80
                  WHEN s.tags LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                    OR s.genre LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                  THEN 60
                  WHEN s.tools LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%')) THEN 50
                  WHEN s.skills LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%'))
                    OR (CAST(:qCanonical AS VARCHAR) <> ''
                        AND s.skills LIKE LOWER(CONCAT('%', CAST(:qCanonical AS VARCHAR), '%')))
                  THEN 40
                  WHEN s.body LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%')) THEN 30
                  WHEN s.author LIKE LOWER(CONCAT('%', CAST(:q AS VARCHAR), '%')) THEN 20
                  ELSE 10
                END
            ) DESC,
            cp.pinned_at DESC NULLS LAST,
            cp.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM content_posts cp
            INNER JOIN users u ON u.id = cp.creator_id
            LEFT JOIN creator_profiles prof ON prof.user_id = cp.creator_id
            CROSS JOIN LATERAL (
                SELECT CONCAT_WS(' ',
                    LOWER(COALESCE(cp.title, '')),
                    LOWER(COALESCE(cp.genre, '')),
                    LOWER(COALESCE(cp.tags::text, '')),
                    LOWER(COALESCE(cp.tools_used::text, '')),
                    LOWER(CONCAT_WS(' ', cp.description, cp.mood_label, cp.price_info)),
                    LOWER(CONCAT_WS(' ', prof.specialite, prof.specialties::text, prof.specialty_tags::text,
                        prof.strengths_tools_mastered::text, prof.profile_services::text)),
                    LOWER(CONCAT_WS(' ', u.full_name, prof.shop_name, prof.bio))
                ) AS haystack
            ) s
            WHERE cp.deleted_at IS NULL
            AND u.deleted_at IS NULL
            AND cp.is_public = true
            AND cp.archived_at IS NULL
            AND (CAST(:creatorId AS UUID) IS NULL OR cp.creator_id = CAST(:creatorId AS UUID))
            AND (CAST(:genre AS VARCHAR) IS NULL OR cp.genre = CAST(:genre AS VARCHAR))
            AND (cp.repost_of_id IS NULL OR EXISTS (
                SELECT 1 FROM content_posts orig
                WHERE orig.id = cp.repost_of_id
                AND orig.deleted_at IS NULL AND orig.is_public = true AND orig.archived_at IS NULL
            ))
            AND (CAST(:viewerId AS UUID) IS NULL OR CAST(:creatorId AS UUID) IS NOT NULL OR NOT EXISTS (
                SELECT 1 FROM content_post_hides h
                WHERE h.user_id = CAST(:viewerId AS UUID) AND h.post_id = cp.id
            ))
            AND (
                CAST(:terms AS VARCHAR) = ''
                OR EXISTS (
                    SELECT 1 FROM unnest(string_to_array(CAST(:terms AS VARCHAR), '|')) AS x(term)
                    WHERE length(btrim(term)) >= 2
                    AND (
                        CASE
                          WHEN LOWER(btrim(term)) ~ '^[a-z0-9]{2,3}$'
                          THEN s.haystack ~ ('\\m' || LOWER(btrim(term)) || '\\M')
                          ELSE s.haystack LIKE CONCAT('%', LOWER(btrim(term)), '%')
                        END
                    )
                )
            )
            """,
            nativeQuery = true)
    Page<ContentPost> findPublicFiltered(
            @Param("creatorId") UUID creatorId,
            @Param("viewerId") UUID viewerId,
            @Param("genre") String genre,
            @Param("q") String q,
            @Param("qCanonical") String qCanonical,
            @Param("terms") String terms,
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

    /** Live (not trashed) reposts per original: rows of {@code [originalId, count]}. */
    @Query("""
            SELECT cp.repostOfId, COUNT(cp) FROM ContentPost cp
            WHERE cp.repostOfId IN :originalIds
            GROUP BY cp.repostOfId
            """)
    List<Object[]> countRepostsByOriginalIds(@Param("originalIds") Collection<UUID> originalIds);

    /** Which of {@code originalIds} the creator has already reposted. */
    @Query("""
            SELECT cp.repostOfId FROM ContentPost cp
            WHERE cp.creator.id = :creatorId AND cp.repostOfId IN :originalIds
            """)
    List<UUID> findRepostedOriginalIds(
            @Param("creatorId") UUID creatorId, @Param("originalIds") Collection<UUID> originalIds);

    Optional<ContentPost> findFirstByCreator_IdAndRepostOfId(UUID creatorId, UUID repostOfId);

    long countByRepostOfId(UUID repostOfId);

    /** Public posts the user saved, most recently saved first. */
    @Query(value = """
            SELECT cp FROM ContentPost cp, ContentFavorite f
            WHERE f.user.id = :userId AND f.targetType = com.plateforme.marketplace.entity.ContentTargetType.POST
            AND f.targetId = cp.id AND cp.isPublic = true AND cp.archivedAt IS NULL
            ORDER BY f.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(cp) FROM ContentPost cp, ContentFavorite f
            WHERE f.user.id = :userId AND f.targetType = com.plateforme.marketplace.entity.ContentTargetType.POST
            AND f.targetId = cp.id AND cp.isPublic = true AND cp.archivedAt IS NULL
            """)
    Page<ContentPost> findSavedByUserId(@Param("userId") UUID userId, Pageable pageable);
}
