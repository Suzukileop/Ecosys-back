package com.plateforme.ecosystem.repository;

import com.plateforme.ecosystem.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    Page<ChatMessage> findByRoomIdOrderBySentAtDesc(String roomId, Pageable pageable);

    List<ChatMessage> findByRoomIdOrderBySentAtAsc(String roomId);
}
