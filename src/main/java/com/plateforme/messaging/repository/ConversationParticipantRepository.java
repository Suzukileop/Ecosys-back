package com.plateforme.messaging.repository;

import com.plateforme.messaging.entity.ConversationParticipant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, UUID> {

    boolean existsByConversation_IdAndUser_Id(UUID conversationId, UUID userId);

    Optional<ConversationParticipant> findByConversation_IdAndUser_Id(UUID conversationId, UUID userId);

    List<ConversationParticipant> findByConversation_Id(UUID conversationId);

    @Query("""
            SELECT cp FROM ConversationParticipant cp
            JOIN FETCH cp.user
            WHERE cp.conversation.id IN :conversationIds
            """)
    List<ConversationParticipant> findByConversation_IdInWithUser(
            @Param("conversationIds") Collection<UUID> conversationIds);

    @Query("""
            SELECT cp FROM ConversationParticipant cp
            JOIN FETCH cp.conversation
            WHERE cp.user.id = :userId
            AND cp.role = com.plateforme.messaging.entity.ParticipantRole.GUEST
            AND cp.leftAt IS NOT NULL
            ORDER BY cp.leftAt DESC
            """)
    List<ConversationParticipant> findEndedGuestParticipationsByUserId(
            @Param("userId") UUID userId,
            Pageable pageable);
}
