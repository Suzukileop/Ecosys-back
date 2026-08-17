package com.plateforme.ecosystem.config;

import com.plateforme.auth.security.CurrentUserUtil;
import com.plateforme.auth.security.JwtUtils;
import com.plateforme.auth.security.UserDetailsServiceImpl;
import com.plateforme.messaging.service.MessagingParticipantGuard;
import com.plateforme.user.entity.User;
import com.plateforme.user.presence.PresenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Pattern CONVERSATION_TOPIC =
            Pattern.compile("^/topic/conversations/([0-9a-fA-F-]{36})(?:/.*)?$");

    private static final Pattern CONVERSATION_APP =
            Pattern.compile("^/app/conversations/([0-9a-fA-F-]{36})(?:/.*)?$");

    private static final Pattern PRESENCE_TOPIC =
            Pattern.compile("^/topic/presence/([0-9a-fA-F-]{36})$");

    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;
    private final MessagingParticipantGuard participantGuard;
    private final PresenceService presenceService;

    public WebSocketConfig(
            JwtUtils jwtUtils,
            UserDetailsServiceImpl userDetailsService,
            MessagingParticipantGuard participantGuard,
            @Lazy PresenceService presenceService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
        this.participantGuard = participantGuard;
        this.presenceService = presenceService;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:3000")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                        message, StompHeaderAccessor.class);

                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");

                    if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        try {
                            if (jwtUtils.validateToken(token)) {
                                String username = jwtUtils.extractUsername(token);
                                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                                UsernamePasswordAuthenticationToken authentication =
                                        new UsernamePasswordAuthenticationToken(
                                                userDetails, null, userDetails.getAuthorities());
                                accessor.setUser(authentication);
                                log.debug("WebSocket CONNECT authentifié pour user={}", username);

                                // Register presence here — SessionConnectedEvent often has a null principal.
                                String sessionId = accessor.getSessionId();
                                if (sessionId != null && userDetails instanceof User user) {
                                    try {
                                        presenceService.sessionConnected(user.getId(), sessionId);
                                    } catch (Exception ex) {
                                        log.warn("Presence connect failed user={}: {}", username, ex.getMessage());
                                    }
                                }
                            } else {
                                log.warn("Token JWT invalide lors de la connexion WebSocket");
                            }
                        } catch (Exception e) {
                            log.warn("Erreur d'authentification WebSocket : {}", e.getMessage());
                        }
                    } else {
                        log.debug("Connexion WebSocket sans token (anonyme)");
                    }
                }

                if (accessor != null && StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                    String sessionId = accessor.getSessionId();
                    if (sessionId != null) {
                        try {
                            presenceService.sessionDisconnected(sessionId);
                        } catch (Exception ex) {
                            log.warn("Presence disconnect failed session={}: {}", sessionId, ex.getMessage());
                        }
                    }
                }

                if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    if (destination != null) {
                        Matcher presenceMatcher = PRESENCE_TOPIC.matcher(destination);
                        if (presenceMatcher.matches()) {
                            if (accessor.getUser() == null) {
                                log.debug("SUBSCRIBE denied presence — unauthenticated");
                                return null;
                            }
                            CurrentUserUtil.requireUser(accessor.getUser());
                        }

                        Matcher matcher = CONVERSATION_TOPIC.matcher(destination);
                        if (matcher.matches() && accessor.getUser() != null) {
                            UUID conversationId = UUID.fromString(matcher.group(1));
                            User user = CurrentUserUtil.requireUser(accessor.getUser());
                            if (!participantGuard.isActiveParticipant(conversationId, user.getId())) {
                                log.debug("SUBSCRIBE denied conversation={} user={}", conversationId, user.getId());
                                return null;
                            }
                        }
                    }
                }

                if (accessor != null && StompCommand.SEND.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    if (destination != null) {
                        Matcher matcher = CONVERSATION_APP.matcher(destination);
                        if (matcher.matches()) {
                            if (accessor.getUser() == null) {
                                throw new AccessDeniedException("Authentication required");
                            }
                            UUID conversationId = UUID.fromString(matcher.group(1));
                            User user = CurrentUserUtil.requireUser(accessor.getUser());
                            if (!participantGuard.isActiveParticipant(conversationId, user.getId())) {
                                log.debug("SEND denied conversation={} user={}", conversationId, user.getId());
                                return null;
                            }
                        }
                    }
                }

                return message;
            }
        });
    }
}
