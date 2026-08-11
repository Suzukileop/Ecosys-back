package com.plateforme.messaging.repository;

import com.plateforme.messaging.entity.ConversationInvite;
import com.plateforme.messaging.entity.InviteStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationInviteRepository extends JpaRepository<ConversationInvite, UUID> {

    Optional<ConversationInvite> findByToken(String token);

    List<ConversationInvite> findByInvitee_IdAndStatusOrderByCreatedAtDesc(UUID inviteeId, InviteStatus status);

    List<ConversationInvite> findByConversation_IdAndStatus(UUID conversationId, InviteStatus status);

    Optional<ConversationInvite> findByConversation_IdAndInvitee_IdAndStatus(
            UUID conversationId, UUID inviteeId, InviteStatus status);

    List<ConversationInvite> findByCreatedBy_IdAndStatusAndInviteeIsNotNullOrderByCreatedAtDesc(
            UUID createdById, InviteStatus status);
}
