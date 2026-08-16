package com.plateforme.messaging.repository;

import com.plateforme.messaging.entity.DirectMessage;
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
public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> {

    Page<DirectMessage> findByConversation_IdOrderBySentAtDesc(UUID conversationId, Pageable pageable);

    Page<DirectMessage> findByConversation_IdAndSentAtGreaterThanEqualOrderBySentAtDesc(
            UUID conversationId, LocalDateTime sentAt, Pageable pageable);

    Page<DirectMessage> findByConversation_IdAndSentAtBetweenOrderBySentAtDesc(
            UUID conversationId, LocalDateTime sentAtAfter, LocalDateTime sentAtBefore, Pageable pageable);

    Optional<DirectMessage> findFirstByConversation_IdOrderBySentAtDesc(UUID conversationId);

    List<DirectMessage> findTop30ByConversation_IdOrderBySentAtDesc(UUID conversationId);

    long countByConversation_IdAndSender_IdNotAndSentAtAfter(
            UUID conversationId, UUID senderId, LocalDateTime after);

    @Query(value = """
            WITH direct_conversations AS (
                SELECT DISTINCT cp.conversation_id
                FROM conversation_participants cp
                INNER JOIN conversations c ON c.id = cp.conversation_id
                WHERE cp.user_id = :creatorId
                  AND c.type = 'DIRECT'
                  AND cp.left_at IS NULL
            ),
            client_messages AS (
                SELECT dm.conversation_id, dm.sent_at
                FROM direct_messages dm
                INNER JOIN direct_conversations dc ON dc.conversation_id = dm.conversation_id
                WHERE dm.sender_id <> :creatorId
                  AND dm.message_type IN ('TEXT', 'FILE')
            ),
            response_pairs AS (
                SELECT EXTRACT(EPOCH FROM (
                    (SELECT MIN(r.sent_at)
                     FROM direct_messages r
                     WHERE r.conversation_id = cm.conversation_id
                       AND r.sender_id = :creatorId
                       AND r.sent_at > cm.sent_at
                       AND r.message_type IN ('TEXT', 'FILE')
                    ) - cm.sent_at
                )) AS response_seconds,
                cm.sent_at
                FROM client_messages cm
                WHERE EXISTS (
                    SELECT 1 FROM direct_messages r
                    WHERE r.conversation_id = cm.conversation_id
                      AND r.sender_id = :creatorId
                      AND r.sent_at > cm.sent_at
                      AND r.message_type IN ('TEXT', 'FILE')
                )
            )
            SELECT response_seconds
            FROM response_pairs
            WHERE response_seconds IS NOT NULL AND response_seconds >= 0
            ORDER BY sent_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Double> findRecentResponseTimeSeconds(
            @Param("creatorId") UUID creatorId,
            @Param("limit") int limit);

    /**
     * Discuss response metrics for a service provider (marketplace “Discuss” DMs).
     * <p>
     * Modeling choice (no schema change): an inbound conversation is a DIRECT chat where
     * the creator is an active participant, {@code conversations.created_by} is the other
     * party (client initiated via Discuss / find-or-create), and at least one TEXT/FILE
     * message exists from a non-creator sender. A reply is the creator’s first TEXT/FILE
     * after that first client message. Response rate and average first-reply latency are
     * derived live from these rows — nothing is cached on the creator profile for this
     * trust-metric card.
     *
     * @return single row: inbound_count, replied_count, avg_first_reply_seconds (nullable)
     */
    @Query(value = """
            WITH inbound AS (
                SELECT c.id AS conversation_id,
                       MIN(CASE
                           WHEN dm.sender_id <> :creatorId
                            AND dm.message_type IN ('TEXT', 'FILE')
                           THEN dm.sent_at
                       END) AS first_client_at
                FROM conversations c
                INNER JOIN conversation_participants cp
                    ON cp.conversation_id = c.id
                   AND cp.user_id = :creatorId
                   AND cp.left_at IS NULL
                LEFT JOIN direct_messages dm ON dm.conversation_id = c.id
                WHERE c.type = 'DIRECT'
                  AND c.created_by IS NOT NULL
                  AND c.created_by <> :creatorId
                GROUP BY c.id
                HAVING MIN(CASE
                    WHEN dm.sender_id <> :creatorId
                     AND dm.message_type IN ('TEXT', 'FILE')
                    THEN dm.sent_at
                END) IS NOT NULL
            ),
            with_reply AS (
                SELECT i.conversation_id,
                       i.first_client_at,
                       (
                           SELECT MIN(r.sent_at)
                           FROM direct_messages r
                           WHERE r.conversation_id = i.conversation_id
                             AND r.sender_id = :creatorId
                             AND r.sent_at > i.first_client_at
                             AND r.message_type IN ('TEXT', 'FILE')
                       ) AS first_reply_at
                FROM inbound i
            )
            SELECT COUNT(*)::bigint AS inbound_count,
                   COUNT(first_reply_at)::bigint AS replied_count,
                   AVG(EXTRACT(EPOCH FROM (first_reply_at - first_client_at))) AS avg_first_reply_seconds
            FROM with_reply
            """, nativeQuery = true)
    List<Object[]> aggregateDiscussResponseStats(@Param("creatorId") UUID creatorId);
}
