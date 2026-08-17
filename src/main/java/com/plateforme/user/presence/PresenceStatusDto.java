package com.plateforme.user.presence;

import java.time.Instant;
import java.util.UUID;

public record PresenceStatusDto(
        UUID userId,
        boolean online,
        Instant lastSeenAt
) {
    public static PresenceStatusDto from(PresenceStatus status) {
        return new PresenceStatusDto(status.userId(), status.online(), status.lastSeenAt());
    }
}
