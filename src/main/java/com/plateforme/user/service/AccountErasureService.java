package com.plateforme.user.service;

import com.plateforme.shared.storage.StorageService;
import com.plateforme.marketplace.service.MarketplaceProductReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

/**
 * Erases what a deleted account leaves behind, beyond the anonymised {@code users} row.
 *
 * <p>Kept on purpose: messages sent to other people (they belong to the recipients' conversations too),
 * purchase records (accounting), and audit logs (security). Native SQL is used
 * because several entities carry {@code @SQLRestriction("deleted_at IS NULL")} and would hide trashed rows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountErasureService {

    private static final String DELETED_NAME = "Deleted user";

    private final JdbcTemplate jdbc;
    private final StorageService storageService;
    private final MarketplaceProductReviewService reviewService;

    @Transactional(propagation = Propagation.MANDATORY)
    public void erase(UUID userId, String formerFullName) {
        List<UUID> reviewedProductIds = ids(
                "SELECT DISTINCT product_id FROM marketplace_product_reviews WHERE user_id = ?", userId);
        List<UUID> votedReviewIds = ids(
                "SELECT DISTINCT review_id FROM marketplace_product_review_helpful_votes WHERE user_id = ?", userId);

        eraseReactionsOnOthers(userId);
        eraseOwnContent(userId);
        eraseReviews(userId, reviewedProductIds, votedReviewIds);
        eraseProfileAndGraph(userId);
        eraseAccountData(userId);
        scrubConversations(userId, formerFullName);

        scheduleFileDeletion(userId);
        log.info("Account data erased user={}", userId);
    }

    /** Likes, dislikes and favourites the user left on other people's posts and products. */
    private void eraseReactionsOnOthers(UUID userId) {
        jdbc.update("""
                UPDATE content_posts SET likes = GREATEST(likes - 1, 0)
                WHERE id IN (SELECT target_id FROM content_reactions
                             WHERE user_id = ? AND target_type = 'POST' AND type = 'LIKE')""", userId);
        jdbc.update("""
                UPDATE marketplace_products SET likes = GREATEST(likes - 1, 0)
                WHERE id IN (SELECT target_id FROM content_reactions
                             WHERE user_id = ? AND target_type = 'PRODUCT' AND type = 'LIKE')""", userId);
        jdbc.update("""
                UPDATE marketplace_products SET dislikes = GREATEST(dislikes - 1, 0)
                WHERE id IN (SELECT target_id FROM content_reactions
                             WHERE user_id = ? AND target_type = 'PRODUCT' AND type = 'DISLIKE')""", userId);
        jdbc.update("""
                UPDATE marketplace_products SET favorites = GREATEST(favorites - 1, 0)
                WHERE id IN (SELECT target_id FROM content_favorites
                             WHERE user_id = ? AND target_type = 'PRODUCT')""", userId);

        jdbc.update("DELETE FROM content_reactions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM content_favorites WHERE user_id = ?", userId);
        jdbc.update("UPDATE content_shares SET user_id = NULL WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM content_reports WHERE reporter_id = ?", userId);
        /* Deleting the rows would cascade to other people's replies, so the text is blanked instead. */
        jdbc.update("""
                UPDATE content_comments SET comment = '', deleted_at = COALESCE(deleted_at, now())
                WHERE user_id = ?""", userId);
        jdbc.update("""
                UPDATE content_posts SET tagged_user_ids = tagged_user_ids - CAST(? AS text)
                WHERE jsonb_exists(tagged_user_ids, CAST(? AS text))""", userId.toString(), userId.toString());
    }

    /** Posts, products, bundles and groups the user created, including trashed ones. */
    private void eraseOwnContent(UUID userId) {
        String ownTargets = """
                ((target_type = 'POST' AND target_id IN (SELECT id FROM content_posts WHERE creator_id = ?))
                 OR (target_type = 'PRODUCT' AND target_id IN (SELECT id FROM marketplace_products WHERE creator_id = ?)))""";
        /* Social rows point at their target without a foreign key, so nothing cascades to them. */
        for (String table : List.of("content_reactions", "content_favorites", "content_shares", "content_reports")) {
            jdbc.update("""
                    DELETE FROM %s WHERE target_type = 'COMMENT' AND target_id IN
                        (SELECT id FROM content_comments WHERE %s)""".formatted(table, ownTargets),
                    userId, userId);
            jdbc.update("DELETE FROM %s WHERE %s".formatted(table, ownTargets), userId, userId);
        }
        jdbc.update("DELETE FROM content_comments WHERE " + ownTargets, userId, userId);

        jdbc.update("DELETE FROM content_posts WHERE creator_id = ?", userId);
        jdbc.update("DELETE FROM marketplace_products WHERE creator_id = ?", userId);
        jdbc.update("DELETE FROM marketplace_bundles WHERE creator_id = ?", userId);
        jdbc.update("DELETE FROM marketplace_product_groups WHERE creator_id = ?", userId);
    }

    private void eraseReviews(UUID userId, List<UUID> reviewedProductIds, List<UUID> votedReviewIds) {
        jdbc.update("DELETE FROM marketplace_product_review_helpful_votes WHERE user_id = ?", userId);
        for (UUID reviewId : votedReviewIds) {
            jdbc.update("""
                    UPDATE marketplace_product_reviews r SET
                        helpful_yes_count = (SELECT COUNT(*) FROM marketplace_product_review_helpful_votes v
                                             WHERE v.review_id = r.id AND v.helpful),
                        helpful_no_count = (SELECT COUNT(*) FROM marketplace_product_review_helpful_votes v
                                            WHERE v.review_id = r.id AND NOT v.helpful)
                    WHERE r.id = ?""", reviewId);
        }
        jdbc.update("DELETE FROM marketplace_product_reviews WHERE user_id = ?", userId);
        reviewedProductIds.forEach(reviewService::refreshProductRating);

        jdbc.update("DELETE FROM marketplace_product_views WHERE user_id = ?", userId);
    }

    private void eraseProfileAndGraph(UUID userId) {
        jdbc.update("DELETE FROM creator_follows WHERE follower_id = ? OR creator_id = ?", userId, userId);
        jdbc.update("DELETE FROM creator_stars WHERE user_id = ? OR creator_id = ?", userId, userId);
        jdbc.update("""
                DELETE FROM creator_profile_visits
                WHERE creator_user_id = ? OR viewer_user_id = ? OR visitor_key = ?""",
                userId, userId, "user:" + userId);
        jdbc.update("DELETE FROM creator_profile_images WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM creator_profiles WHERE user_id = ?", userId);
    }

    private void eraseAccountData(UUID userId) {
        jdbc.update("DELETE FROM user_settings WHERE user_id = ?", userId);
        /* ref_secondary_id holds the actor on other people's notifications ("X followed you"). */
        jdbc.update("DELETE FROM notifications WHERE user_id = ? OR ref_secondary_id = ?", userId, userId);
    }

    /** System lines such as "Jane joined the conversation." have the name baked into their text. */
    private void scrubConversations(UUID userId, String formerFullName) {
        if (formerFullName == null || formerFullName.isBlank() || formerFullName.trim().length() < 2
                || DELETED_NAME.equals(formerFullName.trim())) {
            return;
        }
        jdbc.update("""
                UPDATE direct_messages SET content = replace(content, ?, ?)
                WHERE message_type = 'SYSTEM'
                  AND conversation_id IN (SELECT conversation_id FROM conversation_participants WHERE user_id = ?)""",
                formerFullName.trim(), DELETED_NAME, userId);
    }

    /** Files go only once the database changes are committed, so a rollback never loses media. */
    private void scheduleFileDeletion(UUID userId) {
        List<String> prefixes = List.of(
                "profiles/public/" + userId + "/",
                "content/public/" + userId + "/",
                "content/public/experience/" + userId + "/",
                "marketplace/public/" + userId + "/");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String prefix : prefixes) {
                    try {
                        storageService.deleteByPrefix(prefix);
                    } catch (Exception e) {
                        log.warn("Account erasure: could not delete files prefix={} user={}", prefix, userId, e);
                    }
                }
            }
        });
    }

    private List<UUID> ids(String sql, UUID userId) {
        return jdbc.queryForList(sql, UUID.class, userId);
    }
}
