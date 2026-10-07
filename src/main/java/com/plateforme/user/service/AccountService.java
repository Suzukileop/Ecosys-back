package com.plateforme.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.dto.AccountSecurityDto;
import com.plateforme.user.dto.ChangePasswordRequest;
import com.plateforme.user.dto.DeleteAccountRequest;
import com.plateforme.user.entity.RefreshToken;
import com.plateforme.user.entity.Role;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.CreatorFollowRepository;
import com.plateforme.user.repository.RefreshTokenRepository;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Security and lifecycle operations behind Settings → Security / Your data. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    public static final String DELETE_CONFIRMATION = "DELETE";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CreatorFollowRepository creatorFollowRepository;
    private final UserSettingsService userSettingsService;
    private final AccountErasureService accountErasureService;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AccountSecurityDto getSecurity(UUID userId) {
        User user = requireUser(userId);
        long activeSessions = refreshTokenRepository.findAllByUserAndIsRevokedFalse(user).stream()
                .filter(t -> t.getExpiryDate() != null && t.getExpiryDate().isAfter(LocalDateTime.now()))
                .count();
        return new AccountSecurityDto(
                user.getEmail(),
                Boolean.TRUE.equals(user.getEmailVerified()),
                user.getAuthProvider(),
                hasPassword(user),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                activeSessions
        );
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = requireUser(userId);
        if (hasPassword(user)) {
            String current = request.currentPassword();
            if (current == null || current.isBlank() || !passwordEncoder.matches(current, user.getPasswordHash())) {
                throw new BusinessException("INVALID_CURRENT_PASSWORD", "Your current password is incorrect.");
            }
            if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
                throw new BusinessException("PASSWORD_UNCHANGED", "Choose a password you haven't used here.");
            }
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        log.info("Password changed user={}", userId);
    }

    /** Revokes every refresh token — all devices, including this one, must sign in again. */
    @Transactional
    public int revokeAllSessions(UUID userId) {
        User user = requireUser(userId);
        List<RefreshToken> tokens = refreshTokenRepository.findAllByUserAndIsRevokedFalse(user);
        LocalDateTime now = LocalDateTime.now();
        tokens.forEach(t -> {
            t.setIsRevoked(true);
            t.setRevokedAt(now);
        });
        refreshTokenRepository.saveAll(tokens);
        log.info("All sessions revoked user={} count={}", userId, tokens.size());
        return tokens.size();
    }

    /**
     * Everything Skraft holds about the user (GDPR access / portability). Other people only appear as
     * public usernames, and secrets (password hash, tokens, private storage keys) are left out.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> exportData(UUID userId) {
        User user = requireUser(userId);
        String visitorKey = "user:" + userId;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", LocalDateTime.now().toString());

        Map<String, Object> account = new LinkedHashMap<>();
        account.put("id", user.getId());
        account.put("email", user.getEmail());
        account.put("fullName", user.getFullName());
        account.put("username", user.getPublicUsername());
        account.put("phone", user.getPhone());
        account.put("avatarUrl", user.getAvatarUrl());
        account.put("authProvider", user.getAuthProvider());
        account.put("emailVerified", user.getEmailVerified());
        account.put("roles", user.getRoles().stream().map(Role::getName).toList());
        account.put("createdAt", user.getCreatedAt());
        account.put("lastLoginAt", user.getLastLoginAt());
        out.put("account", account);

        out.put("settings", userSettingsService.getSettings(userId));
        out.put("profile", exportRow("SELECT * FROM creator_profiles WHERE user_id = ?", userId));
        out.put("profilePhotos", exportRows("SELECT * FROM creator_profile_images WHERE user_id = ?", userId));

        Map<String, Object> network = new LinkedHashMap<>();
        network.put("followerCount", creatorFollowRepository.countByCreator_Id(userId));
        network.put("following", exportRows("""
                SELECT u.username, f.created_at FROM creator_follows f JOIN users u ON u.id = f.creator_id
                WHERE f.follower_id = ? AND u.deleted_at IS NULL""", userId));
        network.put("starsGiven", exportRows("""
                SELECT u.username, s.created_at FROM creator_stars s JOIN users u ON u.id = s.creator_id
                WHERE s.user_id = ? AND u.deleted_at IS NULL""", userId));
        network.put("profilesVisited", exportRows("""
                SELECT u.username, v.viewed_at, v.visit_count FROM creator_profile_visits v
                JOIN users u ON u.id = v.creator_user_id
                WHERE (v.viewer_user_id = ? OR v.visitor_key = ?) AND u.deleted_at IS NULL""", userId, visitorKey));
        network.put("visitsToYourProfile", jdbc.queryForObject(
                "SELECT COUNT(*) FROM creator_profile_visits WHERE creator_user_id = ?", Long.class, userId));
        out.put("network", network);

        out.put("posts", exportRows("SELECT * FROM content_posts WHERE creator_id = ?", userId));
        out.put("products", exportRows("SELECT * FROM marketplace_products WHERE creator_id = ?", userId));
        out.put("bundles", exportRows("SELECT * FROM marketplace_bundles WHERE creator_id = ?", userId));
        out.put("productGroups", exportRows("SELECT * FROM marketplace_product_groups WHERE creator_id = ?", userId));

        Map<String, Object> activity = new LinkedHashMap<>();
        activity.put("comments", exportRows("SELECT * FROM content_comments WHERE user_id = ?", userId));
        activity.put("reactions", exportRows("SELECT * FROM content_reactions WHERE user_id = ?", userId));
        activity.put("favorites", exportRows("SELECT * FROM content_favorites WHERE user_id = ?", userId));
        activity.put("shares", exportRows("SELECT * FROM content_shares WHERE user_id = ?", userId));
        activity.put("reportsFiled", exportRows("SELECT * FROM content_reports WHERE reporter_id = ?", userId));
        activity.put("productReviews", exportRows(
                "SELECT * FROM marketplace_product_reviews WHERE user_id = ?", userId));
        out.put("activity", activity);

        out.put("purchases", exportRows("SELECT * FROM marketplace_purchases WHERE buyer_id = ?", userId));
        Map<String, Object> messaging = new LinkedHashMap<>();
        messaging.put("messagesSent", exportRows("""
                SELECT id, conversation_id, message_type, content, sent_at FROM direct_messages
                WHERE sender_id = ? ORDER BY sent_at""", userId));
        messaging.put("attachmentsSent", exportRowsOmitting("""
                SELECT a.* FROM message_attachments a JOIN direct_messages m ON m.id = a.message_id
                WHERE m.sender_id = ?""", "object_key", userId));
        messaging.put("callsStarted", exportRows("SELECT * FROM call_sessions WHERE initiator_id = ?", userId));
        out.put("messaging", messaging);

        out.put("notifications", exportRows("SELECT * FROM notifications WHERE user_id = ?", userId));
        return out;
    }

    private JsonNode exportRows(String sql, Object... args) {
        return exportRowsOmitting(sql, null, args);
    }

    /** Serialises rows through PostgreSQL's {@code to_jsonb}, so jsonb columns come out as real JSON. */
    private JsonNode exportRowsOmitting(String sql, String omitColumn, Object... args) {
        String row = omitColumn == null ? "to_jsonb(t)" : "to_jsonb(t) - '" + omitColumn + "'";
        String json = jdbc.queryForObject(
                "SELECT COALESCE(jsonb_agg(" + row + "), '[]'::jsonb)::text FROM (" + sql + ") t",
                String.class, args);
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read export rows", e);
        }
    }

    private JsonNode exportRow(String sql, UUID userId) {
        JsonNode rows = exportRows(sql, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * The {@code users} row stays (anonymised) for referential integrity: it can no longer sign in,
     * disappears from discovery and frees its email and username. Everything else is erased by
     * {@link AccountErasureService}.
     */
    @Transactional
    public void deleteAccount(UUID userId, DeleteAccountRequest request) {
        User user = requireUser(userId);
        if (!DELETE_CONFIRMATION.equals(request.confirmation() != null ? request.confirmation().trim() : null)) {
            throw new BusinessException("INVALID_CONFIRMATION", "Type DELETE to confirm.");
        }
        if (hasPassword(user)) {
            String password = request.password();
            if (password == null || password.isBlank() || !passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new BusinessException("INVALID_CURRENT_PASSWORD", "Your password is incorrect.");
            }
        }

        revokeAllSessions(userId);
        accountErasureService.erase(userId, user.getFullName());

        String suffix = user.getId().toString().replace("-", "").substring(0, 12);
        user.setDeletedAt(LocalDateTime.now());
        user.setEnabled(false);
        user.setEmail("deleted+" + suffix + "@deleted.skraft");
        user.setPublicUsername("deleted_" + suffix);
        user.setFullName("Deleted user");
        user.setAvatarUrl(null);
        user.setPhone(null);
        user.setPasswordHash(null);
        user.setProviderUserId(null);
        user.setEmailVerifiedAt(null);
        user.setLastLoginAt(null);
        userRepository.save(user);
        log.info("Account deleted user={}", userId);
    }

    private static boolean hasPassword(User user) {
        return user.getPasswordHash() != null && !user.getPasswordHash().isBlank();
    }

    private User requireUser(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found."));
    }
}
