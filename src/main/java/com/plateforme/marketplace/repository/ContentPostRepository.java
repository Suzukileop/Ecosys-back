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

    /** Grouped count for a page of creator cards: [creatorId, count] rows, creators with none omitted. */
    @Query("SELECT c.creator.id, COUNT(c) FROM ContentPost c WHERE c.creator.id IN :creatorIds GROUP BY c.creator.id")
    List<Object[]> countByCreatorIds(@Param("creatorIds") Collection<UUID> creatorIds);

    List<ContentPost> findByCreator_IdOrderByCreatedAtDesc(UUID creatorId);

    /**
     * Public feed search — same keyword pipeline as the creator search ({@code CreatorSearchExpand}):
     * the raw query, its canonical specialty and expanded synonyms/tools are matched against the post
     * and its author's profile (specialties, tags, bio). 2–3 letter alphanumeric terms
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
                          THEN CONCAT_WS(' ', s.title, s.genre, s.tags, s.body, s.skills, s.author)
                               ~ ('\\m' || LOWER(btrim(term)) || '\\M')
                          ELSE CONCAT_WS(' ', s.title, s.genre, s.tags, s.body, s.skills, s.author)
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

    /**
     * The candidates of the ranked search and their relevance — shared by {@link #searchPublicRanked}
     * and its count query. A post qualifies on its OWN text only (title, genre, tags, description):
     * matching the author's name, shop or bio is not enough, since the people blocks already answer
     * that — otherwise a name pulled in every post its owner ever wrote.
     *
     * Relevance, the best tier that applies:
     *   100  a tag or the genre is exactly the query (or its canonical specialty)
     *    80  the title contains the query
     *    60  the tags / genre contain it
     *    40  the description contains it
     *  0–35  share of the query's words found in the post (multi-word queries, any order)
     *    25  a synonym / related tool of the query is found (CreatorSearchExpand)
     * 2–3 letter alphanumeric queries / words ("ai", "ui", "3d") match whole words only.
     */
    String RANKED_SEARCH_CANDIDATES = """
            FROM content_posts p
            INNER JOIN users u ON u.id = p.creator_id
            LEFT JOIN creator_profiles prof ON prof.user_id = p.creator_id
            CROSS JOIN LATERAL (
                SELECT
                    LOWER(TRIM(CAST(:q AS VARCHAR))) AS q,
                    LOWER(TRIM(CAST(:qCanonical AS VARCHAR))) AS qc,
                    LOWER(TRIM(CAST(:q AS VARCHAR))) ~ '^[a-z0-9]{2,3}$' AS short
            ) qq
            CROSS JOIN LATERAL (
                SELECT
                    LOWER(COALESCE(p.title, '')) AS title,
                    LOWER(COALESCE(p.genre, '')) AS genre,
                    LOWER(COALESCE(p.tags::text, '')) AS tags,
                    LOWER(CONCAT_WS(' ', p.description, p.mood_label, p.price_info)) AS body,
                    LOWER(CONCAT_WS(' ', p.title, p.genre, p.tags::text, p.description, p.mood_label, p.price_info)) AS own,
                    LOWER(CONCAT_WS(' ', prof.specialite, prof.specialties::text, prof.specialty_tags::text,
                        prof.strengths_tools_mastered::text, prof.profile_services::text)) AS skills
            ) s
            CROSS JOIN LATERAL (
                SELECT GREATEST(
                    CASE WHEN s.genre = qq.q OR (qq.qc <> '' AND s.genre = qq.qc)
                           OR EXISTS (
                               SELECT 1 FROM jsonb_array_elements_text(COALESCE(p.tags, '[]'::jsonb)) tag
                               WHERE LOWER(TRIM(tag)) = qq.q OR (qq.qc <> '' AND LOWER(TRIM(tag)) = qq.qc)
                           )
                         THEN 100 ELSE 0 END,
                    CASE WHEN (qq.short AND s.title ~ ('\\m' || qq.q || '\\M'))
                           OR (NOT qq.short AND s.title LIKE CONCAT('%', qq.q, '%'))
                         THEN 80 ELSE 0 END,
                    CASE WHEN (qq.short AND CONCAT_WS(' ', s.tags, s.genre) ~ ('\\m' || qq.q || '\\M'))
                           OR (NOT qq.short AND CONCAT_WS(' ', s.tags, s.genre) LIKE CONCAT('%', qq.q, '%'))
                         THEN 60 ELSE 0 END,
                    CASE WHEN (qq.short AND s.body ~ ('\\m' || qq.q || '\\M'))
                           OR (NOT qq.short AND s.body LIKE CONCAT('%', qq.q, '%'))
                         THEN 40 ELSE 0 END,
                    (
                        SELECT 35.0 * COUNT(*) FILTER (WHERE
                                CASE WHEN w ~ '^[a-z0-9]{2,3}$'
                                     THEN s.own ~ ('\\m' || w || '\\M')
                                     ELSE s.own LIKE CONCAT('%', w, '%')
                                END)
                               / NULLIF(COUNT(*), 0)
                        FROM regexp_split_to_table(qq.q, '\\s+') AS w
                        WHERE length(w) >= 2
                    ),
                    CASE WHEN EXISTS (
                            SELECT 1 FROM unnest(string_to_array(CAST(:terms AS VARCHAR), '|')) AS x(term)
                            WHERE length(btrim(term)) >= 2
                            AND (
                                CASE WHEN LOWER(btrim(term)) ~ '^[a-z0-9]{2,3}$'
                                     THEN s.own ~ ('\\m' || LOWER(btrim(term)) || '\\M')
                                     ELSE s.own LIKE CONCAT('%', LOWER(btrim(term)), '%')
                                END
                            )
                        )
                         THEN 25 ELSE 0 END
                ) AS relevance
            ) r
            WHERE p.deleted_at IS NULL
            AND u.deleted_at IS NULL
            AND p.is_public = true
            AND p.archived_at IS NULL
            AND (CAST(:genre AS VARCHAR) IS NULL OR p.genre = CAST(:genre AS VARCHAR))
            AND (p.repost_of_id IS NULL OR EXISTS (
                SELECT 1 FROM content_posts orig
                WHERE orig.id = p.repost_of_id
                AND orig.deleted_at IS NULL AND orig.is_public = true AND orig.archived_at IS NULL
            ))
            AND (CAST(:viewerId AS UUID) IS NULL OR NOT EXISTS (
                SELECT 1 FROM content_post_hides h
                WHERE h.user_id = CAST(:viewerId AS UUID) AND h.post_id = p.id
            ))
            AND r.relevance > 0
            """;

    /**
     * Global search over public posts, ranked the way large networks rank a search ("Top" on X,
     * YouTube's relevance): what the post says first, then how it is doing, then who wrote it.
     *
     *   score = relevance                                     (RANKED_SEARCH_CANDIDATES, 25–100)
     *         × engagement  1 + 0.25·ln(1 + 2·likes + 3·comments + 0.05·views) — logarithmic, so a
     *                       viral post cannot bury a better match
     *         × freshness   0.5 + 0.5 / (1 + age / 14 days)^0.6                 — decays, never below ½
     *         × authority   1.15 when the author's specialties / tools match the query
     *         × affinity    1.25 when the viewer follows the author
     *
     * Then diversity: an author's 2nd best match counts ×0.6, the 3rd ×0.36… so one prolific author
     * cannot fill the page and other authors' best posts surface between theirs.
     */
    @Query(value = """
            SELECT cp.* FROM content_posts cp
            INNER JOIN (
                SELECT scored.id,
                       scored.score * POWER(0.6, LEAST(ROW_NUMBER() OVER (
                           PARTITION BY scored.creator_id
                           ORDER BY scored.score DESC, scored.created_at DESC
                       ) - 1, 8)) AS final_score
                FROM (
                    SELECT p.id, p.creator_id, p.created_at,
                        r.relevance
                        * (1 + 0.25 * LN(1
                            + 2.0 * COALESCE(p.likes, 0)
                            + 3.0 * (
                                SELECT COUNT(*) FROM content_comments c
                                WHERE c.target_type = 'POST' AND c.target_id = p.id
                                AND c.deleted_at IS NULL AND c.hidden_at IS NULL
                            )
                            + 0.05 * COALESCE(p.views, 0)))
                        * (0.5 + 0.5 / POWER(1 + GREATEST(EXTRACT(EPOCH FROM (LOCALTIMESTAMP - p.created_at)), 0) / 1209600.0, 0.6))
                        * (CASE WHEN s.skills LIKE CONCAT('%', qq.q, '%')
                                  OR (qq.qc <> '' AND s.skills LIKE CONCAT('%', qq.qc, '%'))
                                THEN 1.15 ELSE 1 END)
                        * (CASE WHEN CAST(:viewerId AS UUID) IS NOT NULL AND EXISTS (
                                    SELECT 1 FROM creator_follows f
                                    WHERE f.follower_id = CAST(:viewerId AS UUID) AND f.creator_id = p.creator_id
                                )
                                THEN 1.25 ELSE 1 END) AS score
            """ + RANKED_SEARCH_CANDIDATES + """
                ) scored
            ) ranked ON ranked.id = cp.id
            ORDER BY ranked.final_score DESC, cp.created_at DESC
            """,
            countQuery = "SELECT COUNT(*) " + RANKED_SEARCH_CANDIDATES,
            nativeQuery = true)
    Page<ContentPost> searchPublicRanked(
            @Param("viewerId") UUID viewerId,
            @Param("genre") String genre,
            @Param("q") String q,
            @Param("qCanonical") String qCanonical,
            @Param("terms") String terms,
            Pageable pageable);

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

    /** Public posts filed in a saved collection, most recently filed first. */
    @Query(value = """
            SELECT cp FROM ContentPost cp, SavedCollectionItem i
            WHERE i.collection.id = :collectionId AND i.postId = cp.id
            AND cp.isPublic = true AND cp.archivedAt IS NULL
            ORDER BY i.addedAt DESC
            """,
            countQuery = """
            SELECT COUNT(cp) FROM ContentPost cp, SavedCollectionItem i
            WHERE i.collection.id = :collectionId AND i.postId = cp.id
            AND cp.isPublic = true AND cp.archivedAt IS NULL
            """)
    Page<ContentPost> findInSavedCollection(@Param("collectionId") UUID collectionId, Pageable pageable);
}
