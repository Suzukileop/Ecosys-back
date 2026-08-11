package com.plateforme.messaging.repository;

import com.plateforme.messaging.entity.MessageAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, UUID> {

    List<MessageAttachment> findByMessage_IdIn(List<UUID> messageIds);

    Optional<MessageAttachment> findByIdAndMessage_Conversation_Id(UUID id, UUID conversationId);
}
