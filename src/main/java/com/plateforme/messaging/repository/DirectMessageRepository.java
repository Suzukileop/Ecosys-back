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
}
