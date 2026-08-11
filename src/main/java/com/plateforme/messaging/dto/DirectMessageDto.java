package com.plateforme.messaging.dto;

import com.plateforme.messaging.entity.MessageType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DirectMessageDto(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String senderName,
        String senderAvatarUrl,
        String content,
        MessageType messageType,
        List<MessageAttachmentDto> attachments,
        LocalDateTime sentAt
) {
}
