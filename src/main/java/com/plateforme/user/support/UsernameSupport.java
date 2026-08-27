package com.plateforme.user.support;

import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.repository.UserRepository;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Public handle rules: unique and case-sensitive ({@code leopard} ≠ {@code Leopard}).
 */
public final class UsernameSupport {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 30;

    /** Letters, digits, underscore — no spaces. */
    public static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9_]{" + MIN_LENGTH + "," + MAX_LENGTH + "}$");

    private UsernameSupport() {}

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static void validateFormat(String username) {
        String normalized = normalize(username);
        if (normalized == null) {
            throw new BusinessException("INVALID_USERNAME", "Username is required");
        }
        if (!PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(
                    "INVALID_USERNAME",
                    "Username must be 3–30 characters: letters, numbers, underscore only"
            );
        }
    }

    public static void requireAvailable(UserRepository userRepository, String username, UUID excludeUserId) {
        validateFormat(username);
        String normalized = normalize(username);
        boolean taken = excludeUserId == null
                ? userRepository.existsByUsername(normalized)
                : userRepository.existsByUsernameAndIdNot(normalized, excludeUserId);
        if (taken) {
            throw new BusinessException(
                    "USERNAME_ALREADY_EXISTS",
                    "This username is already taken"
            );
        }
    }

    /** Derive a valid unique username from email / display name for OAuth / backfill. */
    public static String allocateFromSeed(UserRepository userRepository, String seed, UUID excludeUserId) {
        String base = sanitizeSeed(seed);
        if (base.length() < MIN_LENGTH) {
            base = "user";
        }
        if (base.length() > 24) {
            base = base.substring(0, 24);
        }

        String candidate = base;
        int attempt = 0;
        while (true) {
            validateFormat(candidate);
            boolean taken = excludeUserId == null
                    ? userRepository.existsByUsername(candidate)
                    : userRepository.existsByUsernameAndIdNot(candidate, excludeUserId);
            if (!taken) {
                return candidate;
            }
            attempt++;
            String suffix = "_" + attempt;
            candidate = base.substring(0, Math.min(base.length(), MAX_LENGTH - suffix.length())) + suffix;
            if (attempt > 50) {
                candidate = "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            }
        }
    }

    private static String sanitizeSeed(String seed) {
        if (seed == null || seed.isBlank()) {
            return "user";
        }
        String local = seed.contains("@") ? seed.substring(0, seed.indexOf('@')) : seed;
        String cleaned = local.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        return cleaned.isEmpty() ? "user" : cleaned;
    }
}
