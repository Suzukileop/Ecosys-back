package com.plateforme.messaging.repository;

import com.plateforme.messaging.entity.Conversation;
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
                SELECT cp1.conversation.id FROM ConversationParticipant cp1 WHERE cp1.user.id = :userId1
            )
            AND c.id IN (
                SELECT cp2.conversation.id FROM ConversationParticipant cp2 WHERE cp2.user.id = :userId2
            )
            AND (SELECT COUNT(cp3) FROM ConversationParticipant cp3 WHERE cp3.conversation.id = c.id) = 2
            ORDER BY c.updatedAt DESC
            """)
    List<Conversation> findDirectConversationsBetweenUsersOrderByUpdatedAtDesc(
            @Param("userId1") UUID userId1,
            @Param("userId2") UUID userId2);
}
