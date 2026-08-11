package com.plateforme.messaging.controller;

import com.plateforme.auth.security.CurrentUserUtil;
import com.plateforme.messaging.dto.CallSignalDto;
import com.plateforme.messaging.dto.DirectMessageDto;
import com.plateforme.messaging.dto.SendDirectMessageDto;
import com.plateforme.messaging.dto.SendMessageDeliveredDto;
import com.plateforme.messaging.dto.SendTypingIndicatorDto;
import com.plateforme.messaging.dto.TypingIndicatorDto;
import com.plateforme.messaging.dto.MessageDeliveryReceiptDto;
import com.plateforme.messaging.service.MessagingService;
import com.plateforme.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MessagingStompController {

    private final MessagingService messagingService;

    @MessageMapping("/conversations/{conversationId}/send")
    public DirectMessageDto sendMessage(
            @DestinationVariable UUID conversationId,
            @Valid SendDirectMessageDto dto,
            Principal principal) {

        if (principal == null) {
            throw new IllegalStateException("Authentification requise pour envoyer un message");
        }

        User user = CurrentUserUtil.requireUser(principal);
        return messagingService.sendMessage(conversationId, user.getId(), dto.content());
    }

    @MessageMapping("/conversations/{conversationId}/call/signal")
    @SendTo("/topic/conversations/{conversationId}/call/signal")
    public CallSignalDto relayCallSignal(
            @DestinationVariable UUID conversationId,
            @Payload CallSignalDto signal,
            Principal principal) {

        if (principal == null) {
            throw new IllegalStateException("Authentification requise");
        }

        User user = CurrentUserUtil.requireUser(principal);
        messagingService.assertParticipant(conversationId, user.getId());

        if (signal == null || signal.type() == null || signal.type().isBlank()) {
            throw new IllegalArgumentException("Signal type is required");
        }

        return new CallSignalDto(
                signal.type(),
                signal.payload() != null ? signal.payload() : "",
                user.getId().toString()
        );
    }

    @MessageMapping("/conversations/{conversationId}/typing")
    @SendTo("/topic/conversations/{conversationId}/typing")
    public TypingIndicatorDto relayTyping(
            @DestinationVariable UUID conversationId,
            @Payload SendTypingIndicatorDto dto,
            Principal principal) {

        if (principal == null) {
            throw new IllegalStateException("Authentication required");
        }

        User user = CurrentUserUtil.requireUser(principal);
        messagingService.assertParticipant(conversationId, user.getId());

        return new TypingIndicatorDto(
                user.getId().toString(),
                user.getFullName(),
                dto != null && dto.typing());
    }

    @MessageMapping("/conversations/{conversationId}/delivered")
    @SendTo("/topic/conversations/{conversationId}/delivered")
    public MessageDeliveryReceiptDto relayDelivered(
            @DestinationVariable UUID conversationId,
            @Payload SendMessageDeliveredDto dto,
            Principal principal) {

        if (principal == null) {
            throw new IllegalStateException("Authentication required");
        }

        User user = CurrentUserUtil.requireUser(principal);
        messagingService.assertParticipant(conversationId, user.getId());

        if (dto == null || dto.messageId() == null || dto.messageId().isBlank()) {
            throw new IllegalArgumentException("messageId is required");
        }

        return new MessageDeliveryReceiptDto(
                dto.messageId().trim(),
                user.getId().toString(),
                LocalDateTime.now().toString());
    }
}
