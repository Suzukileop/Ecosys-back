package com.plateforme.messaging.repository;

import com.plateforme.messaging.entity.CallSession;
import com.plateforme.messaging.entity.CallSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CallSessionRepository extends JpaRepository<CallSession, UUID> {

    List<CallSession> findByConversation_IdAndStatusIn(UUID conversationId, List<CallSessionStatus> statuses);

    Optional<CallSession> findByIdAndConversation_Id(UUID id, UUID conversationId);
}
