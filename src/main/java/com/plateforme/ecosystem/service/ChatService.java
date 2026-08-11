package com.plateforme.ecosystem.service;

import com.plateforme.ecosystem.dto.ChatMessageDto;
import com.plateforme.ecosystem.entity.ChatMessage;
import com.plateforme.ecosystem.repository.ChatMessageRepository;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    @Transactional
    public ChatMessage saveMessage(String roomId, UUID senderId, String content) {
        User sender = userRepository.findByIdAndDeletedAtIsNull(senderId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND",
                        "Expéditeur introuvable : " + senderId));

        ChatMessage message = new ChatMessage();
        message.setRoomId(roomId);
        message.setSender(sender);
        message.setContent(content);

        ChatMessage saved = chatMessageRepository.save(message);
        log.debug("Message sauvegardé dans la room={} par sender={}", roomId, senderId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<ChatMessageDto> getHistory(String roomId, Pageable pageable) {
        return chatMessageRepository.findByRoomIdOrderBySentAtDesc(roomId, pageable)
                .map(this::toDto);
    }

    private ChatMessageDto toDto(ChatMessage m) {
        return new ChatMessageDto(
                m.getId(),
                m.getRoomId(),
                m.getSender() != null ? m.getSender().getId() : null,
                m.getSender() != null ? m.getSender().getFullName() : "Bot",
                m.getContent(),
                m.getSentAt(),
                m.getIsRead(),
                m.getSenderType() != null ? m.getSenderType().name() : "HUMAN"
        );
    }
}
