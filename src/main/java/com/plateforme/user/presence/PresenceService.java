package com.plateforme.user.presence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Online presence means a <em>live browser connection</em> only:
 * <ul>
 *   <li>WebSocket session registry (in-memory + Redis mirror with TTL)</li>
 *   <li>HTTP heartbeat TTL while the dashboard tab is open</li>
 *   <li>Short offline grace so refreshes do not flicker</li>
 * </ul>
 * Auth session (refresh cookie) is independent — being logged in does not imply Online.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService {

    static final Duration OFFLINE_GRACE = Duration.ofSeconds(20);
    static final Duration HEARTBEAT_TTL = Duration.ofSeconds(75);
    /** Soft TTL for Redis WS session mirrors — refreshed on each connect/heartbeat. */
    private static final Duration SESSION_MAP_TTL = Duration.ofMinutes(3);

    private static final String SESSIONS_PREFIX = "presence:sessions:";
    private static final String LAST_SEEN_PREFIX = "presence:lastSeen:";
    private static final String SESSION_USER_PREFIX = "presence:session:";
    private static final String GRACE_PREFIX = "presence:grace:";
    private static final String HEARTBEAT_PREFIX = "presence:hb:";

    private final RedisTemplate<String, String> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    /** Local source of truth (avoids depending solely on SessionConnectedEvent + Redis). */
    private final ConcurrentHashMap<String, UUID> sessionToUser = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Set<String>> userSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Instant> heartbeatUntil = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> pendingOffline = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> pendingHeartbeatExpiry = new ConcurrentHashMap<>();
    private final ScheduledExecutorService graceScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "presence-offline-grace");
        t.setDaemon(true);
        return t;
    });

    /**
     * Registers a WebSocket session. Safe to call from the STOMP CONNECT interceptor.
     */
    public boolean sessionConnected(UUID userId, String sessionId) {
        if (userId == null || sessionId == null || sessionId.isBlank()) {
            return false;
        }

        boolean wasOnline = isOnline(userId);
        cancelPendingOffline(userId);
        deleteRedisQuietly(graceKey(userId));

        sessionToUser.put(sessionId, userId);
        userSessions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(sessionId);

        try {
            redisTemplate.opsForValue().set(sessionUserKey(sessionId), userId.toString(), SESSION_MAP_TTL);
            redisTemplate.opsForSet().add(sessionsKey(userId), sessionId);
            redisTemplate.expire(sessionsKey(userId), SESSION_MAP_TTL);
        } catch (Exception ex) {
            log.warn("Presence Redis mirror failed on connect user={}: {}", userId, ex.getMessage());
        }

        if (!wasOnline) {
            broadcast(userId, true, lastSeenAt(userId));
        }
        log.info("Presence ONLINE (ws) user={} session={}", userId, sessionId);
        return !wasOnline;
    }

    /**
     * Removes a WebSocket session. When no sessions and no fresh heartbeat remain, starts offline grace.
     */
    public void sessionDisconnected(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        UUID userId = sessionToUser.remove(sessionId);
        if (userId == null) {
            try {
                String userIdRaw = redisTemplate.opsForValue().get(sessionUserKey(sessionId));
                if (userIdRaw != null && !userIdRaw.isBlank()) {
                    userId = UUID.fromString(userIdRaw);
                }
            } catch (Exception ex) {
                log.debug("Presence disconnect lookup failed session={}: {}", sessionId, ex.getMessage());
            }
        }

        deleteRedisQuietly(sessionUserKey(sessionId));
        if (userId == null) {
            return;
        }

        Set<String> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
            }
        }

        try {
            redisTemplate.opsForSet().remove(sessionsKey(userId), sessionId);
        } catch (Exception ex) {
            log.debug("Presence Redis remove session failed: {}", ex.getMessage());
        }

        if (hasActiveWsSession(userId) || hasFreshHeartbeat(userId)) {
            log.debug("Presence disconnect user={} still online (ws/hb)", userId);
            return;
        }

        beginOfflineGrace(userId);
    }

    /**
     * Marks the authenticated user online via HTTP heartbeat (dashboard open / visible).
     */
    public PresenceStatus heartbeat(UUID userId) {
        if (userId == null) {
            return new PresenceStatus(null, false, null);
        }
        boolean wasOnline = isOnline(userId);
        cancelPendingOffline(userId);
        deleteRedisQuietly(graceKey(userId));

        Instant until = Instant.now().plus(HEARTBEAT_TTL);
        heartbeatUntil.put(userId, until);
        try {
            redisTemplate.opsForValue().set(heartbeatKey(userId), until.toString(), HEARTBEAT_TTL);
            // Keep WS session mirrors alive while the tab is still heartbeating.
            refreshSessionMirrors(userId);
        } catch (Exception ex) {
            log.warn("Presence Redis heartbeat failed user={}: {}", userId, ex.getMessage());
        }

        scheduleHeartbeatExpiry(userId);

        if (!wasOnline) {
            broadcast(userId, true, lastSeenAt(userId));
            log.info("Presence ONLINE (heartbeat) user={}", userId);
        }
        return getStatus(userId);
    }

    /**
     * Immediately marks the user offline (logout, tab close / pagehide).
     * Clears WS mirrors, heartbeat, and grace — then broadcasts offline.
     */
    public PresenceStatus forceOffline(UUID userId) {
        if (userId == null) {
            return new PresenceStatus(null, false, null);
        }

        cancelPendingOffline(userId);
        cancelHeartbeatExpiry(userId);
        heartbeatUntil.remove(userId);

        Set<String> sessions = userSessions.remove(userId);
        if (sessions != null) {
            for (String sessionId : sessions) {
                sessionToUser.remove(sessionId);
                deleteRedisQuietly(sessionUserKey(sessionId));
            }
        }

        Instant seenAt = Instant.now();
        try {
            redisTemplate.delete(sessionsKey(userId));
            redisTemplate.delete(heartbeatKey(userId));
            redisTemplate.delete(graceKey(userId));
            redisTemplate.opsForValue().set(lastSeenKey(userId), seenAt.toString());
        } catch (Exception ex) {
            log.warn("Presence Redis forceOffline failed user={}: {}", userId, ex.getMessage());
        }

        broadcast(userId, false, seenAt);
        log.info("Presence OFFLINE (forced) user={}", userId);
        return new PresenceStatus(userId, false, seenAt);
    }

    public boolean isOnline(UUID userId) {
        if (userId == null) {
            return false;
        }
        if (hasActiveWsSession(userId)) {
            return true;
        }
        if (hasFreshHeartbeat(userId)) {
            return true;
        }
        try {
            Boolean inGrace = redisTemplate.hasKey(graceKey(userId));
            if (Boolean.TRUE.equals(inGrace)) {
                return true;
            }
            if (Boolean.TRUE.equals(redisTemplate.hasKey(heartbeatKey(userId)))) {
                return true;
            }
            // Only trust Redis WS SET members that still have a live session→user key.
            return hasLiveRedisWsSessions(userId);
        } catch (Exception ex) {
            log.debug("Presence Redis isOnline failed user={}: {}", userId, ex.getMessage());
            return false;
        }
    }

    public Instant lastSeenAt(UUID userId) {
        if (userId == null) {
            return null;
        }
        try {
            String raw = redisTemplate.opsForValue().get(lastSeenKey(userId));
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return Instant.parse(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    public Map<UUID, PresenceStatus> getStatuses(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, PresenceStatus> out = new LinkedHashMap<>();
        for (UUID userId : userIds) {
            if (userId == null || out.containsKey(userId)) {
                continue;
            }
            out.put(userId, getStatus(userId));
        }
        return out;
    }

    public PresenceStatus getStatus(UUID userId) {
        return new PresenceStatus(userId, isOnline(userId), lastSeenAt(userId));
    }

    private boolean hasActiveWsSession(UUID userId) {
        Set<String> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    private boolean hasFreshHeartbeat(UUID userId) {
        Instant until = heartbeatUntil.get(userId);
        if (until == null) {
            return false;
        }
        if (until.isAfter(Instant.now())) {
            return true;
        }
        heartbeatUntil.remove(userId, until);
        return false;
    }

    /**
     * Prunes orphaned Redis session ids (SET members without a matching session→user key).
     * Returns true only if at least one live member remains.
     */
    private boolean hasLiveRedisWsSessions(UUID userId) {
        try {
            Set<String> members = redisTemplate.opsForSet().members(sessionsKey(userId));
            if (members == null || members.isEmpty()) {
                return false;
            }
            Set<String> orphans = new HashSet<>();
            boolean anyLive = false;
            for (String sessionId : members) {
                if (sessionId == null || sessionId.isBlank()) {
                    orphans.add(sessionId);
                    continue;
                }
                Boolean exists = redisTemplate.hasKey(sessionUserKey(sessionId));
                if (Boolean.TRUE.equals(exists)) {
                    anyLive = true;
                } else {
                    orphans.add(sessionId);
                }
            }
            if (!orphans.isEmpty()) {
                redisTemplate.opsForSet().remove(sessionsKey(userId), orphans.toArray());
            }
            if (!anyLive) {
                redisTemplate.delete(sessionsKey(userId));
            }
            return anyLive;
        } catch (Exception ex) {
            log.debug("Presence Redis prune failed user={}: {}", userId, ex.getMessage());
            return false;
        }
    }

    private void refreshSessionMirrors(UUID userId) {
        try {
            Set<String> members = redisTemplate.opsForSet().members(sessionsKey(userId));
            if (members == null || members.isEmpty()) {
                return;
            }
            for (String sessionId : members) {
                if (sessionId == null || sessionId.isBlank()) {
                    continue;
                }
                String key = sessionUserKey(sessionId);
                if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                    redisTemplate.expire(key, SESSION_MAP_TTL);
                }
            }
            redisTemplate.expire(sessionsKey(userId), SESSION_MAP_TTL);
        } catch (Exception ex) {
            log.debug("Presence Redis session refresh failed user={}: {}", userId, ex.getMessage());
        }
    }

    private void scheduleHeartbeatExpiry(UUID userId) {
        cancelHeartbeatExpiry(userId);
        long delayMs = HEARTBEAT_TTL.toMillis() + 1_500L;
        ScheduledFuture<?> future = graceScheduler.schedule(() -> {
            pendingHeartbeatExpiry.remove(userId);
            try {
                if (hasActiveWsSession(userId) || hasFreshHeartbeat(userId)) {
                    return;
                }
                try {
                    if (Boolean.TRUE.equals(redisTemplate.hasKey(heartbeatKey(userId)))) {
                        return;
                    }
                    if (hasLiveRedisWsSessions(userId)) {
                        return;
                    }
                } catch (Exception ignored) {
                    /* fall through to offline */
                }
                beginOfflineGrace(userId);
            } catch (Exception ex) {
                log.warn("Presence heartbeat expiry check failed user={}: {}", userId, ex.getMessage());
            }
        }, delayMs, TimeUnit.MILLISECONDS);
        pendingHeartbeatExpiry.put(userId, future);
    }

    private void cancelHeartbeatExpiry(UUID userId) {
        ScheduledFuture<?> pending = pendingHeartbeatExpiry.remove(userId);
        if (pending != null) {
            pending.cancel(false);
        }
    }

    private void beginOfflineGrace(UUID userId) {
        Instant seenAt = Instant.now();
        try {
            redisTemplate.delete(sessionsKey(userId));
            redisTemplate.delete(heartbeatKey(userId));
            heartbeatUntil.remove(userId);
            redisTemplate.opsForValue().set(lastSeenKey(userId), seenAt.toString());
            redisTemplate.opsForValue().set(graceKey(userId), "1", OFFLINE_GRACE);
        } catch (Exception ex) {
            log.warn("Presence Redis offline grace failed user={}: {}", userId, ex.getMessage());
        }
        scheduleOfflineBroadcast(userId, seenAt);
        log.info("Presence grace start user={} ({}s)", userId, OFFLINE_GRACE.toSeconds());
    }

    private void scheduleOfflineBroadcast(UUID userId, Instant seenAt) {
        cancelPendingOffline(userId);
        ScheduledFuture<?> future = graceScheduler.schedule(() -> {
            pendingOffline.remove(userId);
            try {
                if (hasActiveWsSession(userId) || hasFreshHeartbeat(userId)) {
                    deleteRedisQuietly(graceKey(userId));
                    return;
                }
                try {
                    if (Boolean.TRUE.equals(redisTemplate.hasKey(heartbeatKey(userId)))) {
                        deleteRedisQuietly(graceKey(userId));
                        return;
                    }
                    if (hasLiveRedisWsSessions(userId)) {
                        deleteRedisQuietly(graceKey(userId));
                        return;
                    }
                } catch (Exception ignored) {
                    /* local maps already checked */
                }
                deleteRedisQuietly(graceKey(userId));
                broadcast(userId, false, seenAt);
                log.info("Presence OFFLINE user={}", userId);
            } catch (Exception ex) {
                log.warn("Presence offline broadcast failed user={}: {}", userId, ex.getMessage());
            }
        }, OFFLINE_GRACE.toMillis(), TimeUnit.MILLISECONDS);
        pendingOffline.put(userId, future);
    }

    private void cancelPendingOffline(UUID userId) {
        ScheduledFuture<?> pending = pendingOffline.remove(userId);
        if (pending != null) {
            pending.cancel(false);
        }
    }

    private void broadcast(UUID userId, boolean online, Instant lastSeenAt) {
        PresenceStatusDto payload = new PresenceStatusDto(userId, online, lastSeenAt);
        messagingTemplate.convertAndSend("/topic/presence/" + userId, payload);
    }

    private void deleteRedisQuietly(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception ignored) {
            /* optional mirror */
        }
    }

    private static String sessionsKey(UUID userId) {
        return SESSIONS_PREFIX + userId;
    }

    private static String lastSeenKey(UUID userId) {
        return LAST_SEEN_PREFIX + userId;
    }

    private static String sessionUserKey(String sessionId) {
        return SESSION_USER_PREFIX + sessionId;
    }

    private static String graceKey(UUID userId) {
        return GRACE_PREFIX + userId;
    }

    private static String heartbeatKey(UUID userId) {
        return HEARTBEAT_PREFIX + userId;
    }
}
