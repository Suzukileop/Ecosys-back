package com.plateforme.user.presence;

import java.time.Instant;
import java.util.UUID;

/**
 * Snapshot of a user's online presence.
 */
public record PresenceStatus(
        UUID userId,
        boolean online,
        Instant lastSeenAt
) {}
