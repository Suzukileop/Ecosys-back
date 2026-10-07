package com.plateforme.messaging.repository;

import com.plateforme.messaging.entity.Conversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @Query("""
            SELECT c FROM Conversation c
            WHERE c.id IN (
                SELECT cp.conversation.id FROM ConversationParticipant cp WHERE cp.user.id = :userId
            )
            ORDER BY c.updatedAt DESC
            """)
    List<Conversation> findAllForUserOrderByUpdatedAtDesc(@Param("userId") UUID userId);

    @Query("""
            SELECT c FROM Conversation c
            WHERE c.id IN (
                SELECT cp.conversation.id FROM ConversationParticipant cp WHERE cp.user.id = :userId
            )
            ORDER BY c.updatedAt DESC
            """)
    List<Conversation> findRecentForUser(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Direct threads between two members, newest first — the same thread the inbox shows for that
     * peer. Invited guests do not turn a direct thread into another conversation, so they are not
     * counted.
     */
    @Query("""
            SELECT c FROM Conversation c
            WHERE c.type = com.plateforme.messaging.entity.ConversationType.DIRECT
            AND c.temporarySession = false
            AND c.id IN (
                SELECT cp1.conversation.id FROM ConversationParticipant cp1
                WHERE cp1.user.id = :userId1
                AND cp1.role <> com.plateforme.messaging.entity.ParticipantRole.GUEST
                AND cp1.leftAt IS NULL
            )
            AND c.id IN (
                SELECT cp2.conversation.id FROM ConversationParticipant cp2
                WHERE cp2.user.id = :userId2
                AND cp2.role <> com.plateforme.messaging.entity.ParticipantRole.GUEST
            )
            ORDER BY c.updatedAt DESC
            """)
    List<Conversation> findDirectConversationsBetweenUsersOrderByUpdatedAtDesc(
            @Param("userId1") UUID userId1,
            @Param("userId2") UUID userId2);
}
