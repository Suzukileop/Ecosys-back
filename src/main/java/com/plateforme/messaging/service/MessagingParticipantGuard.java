package com.plateforme.messaging.service;

import com.plateforme.messaging.entity.ConversationParticipant;
import com.plateforme.messaging.repository.ConversationParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/** Participant checks without WebSocket/STOMP dependencies (avoids circular refs with WebSocketConfig). */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessagingParticipantGuard {

    private final ConversationParticipantRepository participantRepository;

    @Transactional(readOnly = true)
    public boolean isActiveParticipant(UUID conversationId, UUID userId) {
        return participantRepository.findByConversation_IdAndUser_Id(conversationId, userId)
                .filter(this::isParticipantActive)
                .isPresent();
    }

    @Transactional(readOnly = true)
    public ConversationParticipant requireActiveParticipant(UUID conversationId, UUID userId) {
        ConversationParticipant participant = participantRepository.findByConversation_IdAndUser_Id(conversationId, userId)
                .orElseThrow(() -> {
                    log.warn("Accès refusé à la conversation={} pour user={}", conversationId, userId);
                    return new AccessDeniedException("Vous n'êtes pas participant de cette conversation");
                });
        if (!isParticipantActive(participant)) {
            throw new AccessDeniedException("Your access to this conversation has expired.");
        }
        return participant;
    }

    public boolean isParticipantActive(ConversationParticipant participant) {
        if (participant.getLeftAt() != null) return false;
        return participant.getExpiresAt() == null || participant.getExpiresAt().isAfter(LocalDateTime.now());
    }
}
