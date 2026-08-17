package com.plateforme.user.presence;

import com.plateforme.auth.security.CurrentUserUtil;
import com.plateforme.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * Fallback presence tracking for abrupt socket closes.
 * Primary registration happens in {@code WebSocketConfig} on STOMP CONNECT
 * (SessionConnectedEvent often has a null principal with JWT-in-CONNECT auth).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceEventListener {

    private final PresenceService presenceService;

    @EventListener
    public void onSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        Principal principal = event.getUser() != null ? event.getUser() : accessor.getUser();
        User user = CurrentUserUtil.extractUser(principal);
        if (user == null || sessionId == null) {
            // Expected often — CONNECT interceptor already registered presence.
            return;
        }
        presenceService.sessionConnected(user.getId(), sessionId);
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        presenceService.sessionDisconnected(sessionId);
    }
}
