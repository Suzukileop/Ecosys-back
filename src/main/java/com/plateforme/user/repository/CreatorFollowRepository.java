package com.plateforme.user.repository;

import com.plateforme.user.entity.CreatorFollow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreatorFollowRepository extends JpaRepository<CreatorFollow, UUID> {

    boolean existsByFollower_IdAndCreator_Id(UUID followerId, UUID creatorId);

    Optional<CreatorFollow> findByFollower_IdAndCreator_Id(UUID followerId, UUID creatorId);

    long countByCreator_Id(UUID creatorId);

    Page<CreatorFollow> findByFollower_IdOrderByCreatedAtDesc(UUID followerId, Pageable pageable);

    Page<CreatorFollow> findByCreator_IdOrderByCreatedAtDesc(UUID creatorId, Pageable pageable);

    /**
     * Messenger-style audience ranking: recent DIRECT chat activity first, then frequency,
     * then follow date for followers with no conversation.
     */
    @Query(
            value = """
                    SELECT cf.*
                    FROM creator_follows cf
                    LEFT JOIN (
                        SELECT cp_follower.user_id AS follower_id,
                               MAX(dm.sent_at) AS last_sent_at,
                               COUNT(dm.id) AS message_count
                        FROM conversations c
                        INNER JOIN conversation_participants cp_creator
                            ON cp_creator.conversation_id = c.id
                           AND cp_creator.user_id = :creatorId
                           AND cp_creator.left_at IS NULL
                        INNER JOIN conversation_participants cp_follower
                            ON cp_follower.conversation_id = c.id
                           AND cp_follower.user_id <> :creatorId
                           AND cp_follower.left_at IS NULL
                        LEFT JOIN direct_messages dm
                            ON dm.conversation_id = c.id
                        WHERE c.type = 'DIRECT'
                          AND c.temporary_session = false
                        GROUP BY cp_follower.user_id
                    ) activity ON activity.follower_id = cf.follower_id
                    WHERE cf.creator_id = :creatorId
                    ORDER BY activity.last_sent_at DESC NULLS LAST,
                             COALESCE(activity.message_count, 0) DESC,
                             cf.created_at DESC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM creator_follows cf
                    WHERE cf.creator_id = :creatorId
                    """,
            nativeQuery = true)
    Page<CreatorFollow> findFollowersRankedByMessagingActivity(
            @Param("creatorId") UUID creatorId,
            Pageable pageable);

    @Query("""
            SELECT cf.creator.id FROM CreatorFollow cf
            WHERE cf.follower.id = :followerId AND cf.creator.id IN :creatorIds
            """)
    List<UUID> findFollowedCreatorIds(
            @Param("followerId") UUID followerId,
            @Param("creatorIds") Collection<UUID> creatorIds);

    @Query("""
            SELECT cf.creator.id, COUNT(cf) FROM CreatorFollow cf
            WHERE cf.creator.id IN :creatorIds
            GROUP BY cf.creator.id
            """)
    List<Object[]> countFollowersGrouped(@Param("creatorIds") Collection<UUID> creatorIds);

    @Query("""
            SELECT cf.follower.id FROM CreatorFollow cf
            WHERE cf.creator.id = :creatorId
            """)
    List<UUID> findFollowerIdsByCreatorId(@Param("creatorId") UUID creatorId);
}
