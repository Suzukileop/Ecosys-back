package com.plateforme.marketplace.service;

import com.plateforme.marketplace.dto.*;
import com.plateforme.marketplace.entity.*;
import com.plateforme.marketplace.repository.*;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.shared.service.NotificationService;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketplaceSocialService {

    private final ContentReactionRepository reactionRepository;
    private final ContentFavoriteRepository favoriteRepository;
    private final ContentCommentRepository commentRepository;
    private final ContentReportRepository reportRepository;
    private final ContentShareRepository shareRepository;
    private final ContentPostRepository contentPostRepository;
    private final MarketplaceProductRepository productRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public void addReaction(UUID userId, ContentTargetType targetType, UUID targetId, ReactionType type) {
        if (type == null) {
            throw new BusinessException("INVALID_REACTION", "Reaction type is required");
        }
        validateTargetExists(targetType, targetId);
        User user = requireUser(userId);

        var existing = reactionRepository.findByUser_IdAndTargetTypeAndTargetId(userId, targetType, targetId);
        if (existing.isPresent()) {
            ContentReaction reaction = existing.get();
            if (reaction.getType() == type) {
                return;
            }
            ReactionType previous = reaction.getType();
            reaction.setType(type);
            reactionRepository.save(reaction);
            adjustReactionCount(targetType, targetId, previous, -1);
            adjustReactionCount(targetType, targetId, type, 1);
            log.debug("Reaction switched user={} target={}/{} type={}", userId, targetType, targetId, type);
            return;
        }

        ContentReaction reaction = new ContentReaction();
        reaction.setUser(user);
        reaction.setTargetType(targetType);
        reaction.setTargetId(targetId);
        reaction.setType(type);
        reactionRepository.save(reaction);
        adjustReactionCount(targetType, targetId, type, 1);
        log.info("Reaction added user={} target={}/{} type={}", userId, targetType, targetId, type);
    }

    @Transactional
    public void removeReaction(UUID userId, ContentTargetType targetType, UUID targetId) {
        var existing = reactionRepository.findByUser_IdAndTargetTypeAndTargetId(userId, targetType, targetId);
        if (existing.isEmpty()) {
            return;
        }
        ReactionType previous = existing.get().getType();
        reactionRepository.delete(existing.get());
        adjustReactionCount(targetType, targetId, previous, -1);
        log.info("Reaction removed user={} target={}/{}", userId, targetType, targetId);
    }

    @Transactional(readOnly = true)
    public ReactionCountsResponse getReactionCounts(ContentTargetType targetType, UUID targetId, UUID userId) {
        validateTargetExists(targetType, targetId);
        long likes = reactionRepository.countByTargetTypeAndTargetIdAndType(
                targetType, targetId, ReactionType.LIKE);
        long dislikes = reactionRepository.countByTargetTypeAndTargetIdAndType(
                targetType, targetId, ReactionType.DISLIKE);

        ReactionType userReaction = null;
        boolean favorited = false;
        if (userId != null) {
            userReaction = reactionRepository.findByUser_IdAndTargetTypeAndTargetId(userId, targetType, targetId)
                    .map(ContentReaction::getType)
                    .orElse(null);
            favorited = favoriteRepository.findByUser_IdAndTargetTypeAndTargetId(userId, targetType, targetId)
                    .isPresent();
        }

        return new ReactionCountsResponse(targetType, targetId, likes, dislikes, userReaction, favorited);
    }

    @Transactional(readOnly = true)
    public java.util.List<UUID> getMyFavoriteTargetIds(UUID userId, ContentTargetType targetType) {
        return favoriteRepository.findTargetIdsByUser_IdAndTargetType(userId, targetType);
    }

    @Transactional(readOnly = true)
    public java.util.List<UUID> getMyLikedTargetIds(UUID userId, ContentTargetType targetType) {
        return reactionRepository.findTargetIdsByUser_IdAndTargetTypeAndType(
                userId, targetType, ReactionType.LIKE);
    }

    @Transactional
    public void addFavorite(UUID userId, ContentTargetType targetType, UUID targetId) {
        validateTargetExists(targetType, targetId);
        User user = requireUser(userId);

        if (favoriteRepository.findByUser_IdAndTargetTypeAndTargetId(userId, targetType, targetId).isPresent()) {
            return;
        }

        ContentFavorite favorite = new ContentFavorite();
        favorite.setUser(user);
        favorite.setTargetType(targetType);
        favorite.setTargetId(targetId);
        favoriteRepository.save(favorite);
        if (targetType == ContentTargetType.PRODUCT) {
            adjustProductCounter(targetId, "favorites", 1);
        }
        log.info("Favorite added user={} target={}/{}", userId, targetType, targetId);
    }

    @Transactional
    public void removeFavorite(UUID userId, ContentTargetType targetType, UUID targetId) {
        var existing = favoriteRepository.findByUser_IdAndTargetTypeAndTargetId(userId, targetType, targetId);
        if (existing.isEmpty()) {
            return;
        }
        favoriteRepository.delete(existing.get());
        if (targetType == ContentTargetType.PRODUCT) {
            adjustProductCounter(targetId, "favorites", -1);
        }
        log.info("Favorite removed user={} target={}/{}", userId, targetType, targetId);
    }

    @Transactional(readOnly = true)
    public Page<FavoriteResponse> getMyFavorites(UUID userId, Pageable pageable) {
        return favoriteRepository.findByUser_IdOrderByCreatedAtDesc(userId, pageable)
                .map(f -> new FavoriteResponse(f.getId(), f.getTargetType(), f.getTargetId(), f.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> getComments(
            ContentTargetType targetType,
            UUID targetId,
            Pageable pageable,
            UUID viewerUserId,
            boolean includeHidden) {
        rejectProductComments(targetType);
        validateTargetExists(targetType, targetId);
        if (targetType == ContentTargetType.POST && !areCommentsEnabled(targetId)) {
            return Page.empty(pageable);
        }

        boolean showHidden = includeHidden && viewerUserId != null && isPostCreator(viewerUserId, targetId);
        Page<ContentComment> page = commentRepository.findTopLevelVisible(
                targetType, targetId, showHidden, pageable);

        List<UUID> parentIds = page.getContent().stream().map(ContentComment::getId).toList();
        List<ContentComment> replies = parentIds.isEmpty()
                ? List.of()
                : commentRepository.findRepliesVisible(parentIds, showHidden);

        Map<UUID, List<ContentComment>> repliesByParent = replies.stream()
                .collect(Collectors.groupingBy(r -> r.getParent().getId()));

        List<UUID> allIds = new ArrayList<>(parentIds);
        replies.forEach(r -> allIds.add(r.getId()));
        Map<UUID, ReactionStats> reactionStats = loadReactionStats(ContentTargetType.COMMENT, allIds, viewerUserId);

        return page.map(comment -> toCommentResponse(
                comment,
                repliesByParent.getOrDefault(comment.getId(), List.of()),
                reactionStats));
    }

    @Transactional
    public void deleteComment(UUID userId, UUID commentId) {
        ContentComment comment = requireActiveComment(commentId);
        UUID postId = comment.getTargetId();
        boolean isAuthor = comment.getUser() != null && userId.equals(comment.getUser().getId());
        boolean isPostOwner = comment.getTargetType() == ContentTargetType.POST
                && isPostCreator(userId, postId);
        if (!isAuthor && !isPostOwner) {
            throw new BusinessException("COMMENT_FORBIDDEN", "You cannot delete this comment");
        }
        comment.setDeletedAt(LocalDateTime.now());
        commentRepository.save(comment);
        log.info("Comment deleted id={} by user={}", commentId, userId);
    }

    @Transactional
    public void hideComment(UUID userId, UUID commentId) {
        ContentComment comment = requireActiveComment(commentId);
        if (comment.getTargetType() != ContentTargetType.POST) {
            throw new BusinessException("COMMENT_FORBIDDEN", "Only post comments can be hidden");
        }
        if (!isPostCreator(userId, comment.getTargetId())) {
            throw new BusinessException("COMMENT_FORBIDDEN", "Only the post creator can hide comments");
        }
        User moderator = requireUser(userId);
        comment.setHiddenAt(LocalDateTime.now());
        comment.setHiddenBy(moderator);
        commentRepository.save(comment);
        log.info("Comment hidden id={} by creator={}", commentId, userId);
    }

    @Transactional
    public void unhideComment(UUID userId, UUID commentId) {
        ContentComment comment = requireActiveComment(commentId);
        if (comment.getTargetType() != ContentTargetType.POST) {
            throw new BusinessException("COMMENT_FORBIDDEN", "Only post comments can be unhidden");
        }
        if (!isPostCreator(userId, comment.getTargetId())) {
            throw new BusinessException("COMMENT_FORBIDDEN", "Only the post creator can unhide comments");
        }
        comment.setHiddenAt(null);
        comment.setHiddenBy(null);
        commentRepository.save(comment);
        log.info("Comment unhidden id={} by creator={}", commentId, userId);
    }

    @Transactional
    public CommentResponse addComment(UUID userId, CommentRequest request) {
        rejectProductComments(request.targetType());
        validateTargetExists(request.targetType(), request.targetId());
        if (request.targetType() == ContentTargetType.POST) {
            assertCommentsEnabled(request.targetId());
        }
        User user = requireUser(userId);

        ContentComment parent = null;
        if (request.parentId() != null) {
            parent = commentRepository.findById(request.parentId())
                    .orElseThrow(() -> new BusinessException("COMMENT_NOT_FOUND",
                            "Parent comment not found: " + request.parentId()));
            if (parent.getTargetType() != request.targetType()
                    || !parent.getTargetId().equals(request.targetId())) {
                throw new BusinessException("COMMENT_TARGET_MISMATCH",
                        "Parent comment does not belong to the same target");
            }
        }

        ContentComment comment = new ContentComment();
        comment.setTargetType(request.targetType());
        comment.setTargetId(request.targetId());
        comment.setUser(user);
        comment.setComment(request.comment().trim());
        comment.setParent(parent);
        comment = commentRepository.save(comment);

        if (request.targetType() == ContentTargetType.PRODUCT) {
            adjustProductCounter(request.targetId(), "comments", 1);
        }

        UUID creatorId = resolveCreatorId(request.targetType(), request.targetId());
        if (creatorId != null && !creatorId.equals(userId)) {
            notificationService.createAndSend(
                    creatorId,
                    "MARKETPLACE_NEW_COMMENT",
                    "New comment on your content",
                    user.getFullName() + " commented on your " + request.targetType().name().toLowerCase() + ".",
                    "PLATFORM",
                    request.targetId(),
                    comment.getId());
        }

        log.info("Comment added user={} target={}/{}", userId, request.targetType(), request.targetId());
        return toCommentResponse(comment, List.of(), Map.of());
    }

    private ContentComment requireActiveComment(UUID commentId) {
        ContentComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException("COMMENT_NOT_FOUND",
                        "Comment not found: " + commentId));
        if (comment.getDeletedAt() != null) {
            throw new BusinessException("COMMENT_NOT_FOUND", "Comment not found: " + commentId);
        }
        return comment;
    }

    private boolean areCommentsEnabled(UUID postId) {
        return contentPostRepository.findById(postId)
                .map(post -> Boolean.TRUE.equals(post.getCommentsEnabled()))
                .orElse(false);
    }

    private void assertCommentsEnabled(UUID postId) {
        if (!areCommentsEnabled(postId)) {
            throw new BusinessException("COMMENTS_DISABLED",
                    "Comments are disabled for this content");
        }
    }

    private boolean isPostCreator(UUID userId, UUID postId) {
        return contentPostRepository.findById(postId)
                .map(post -> post.getCreator() != null && userId.equals(post.getCreator().getId()))
                .orElse(false);
    }

    @Transactional
    public ReportResponse submitReport(UUID userId, ReportRequest request) {
        validateTargetExists(request.targetType(), request.targetId());
        User reporter = requireUser(userId);

        ContentReport report = new ContentReport();
        report.setTargetType(request.targetType());
        report.setTargetId(request.targetId());
        report.setReporter(reporter);
        report.setReason(request.reason());
        report.setDetails(request.details());
        report.setStatus(ReportStatus.PENDING);
        report = reportRepository.save(report);

        notificationService.sendBulkToRole(
                "ROLE_ADMIN",
                "MARKETPLACE_REPORT",
                "New content report",
                "A user reported " + request.targetType().name().toLowerCase()
                        + " for reason: " + request.reason().name() + ".",
                report.getId());

        log.info("Report submitted id={} reporter={} target={}/{}", report.getId(), userId,
                request.targetType(), request.targetId());
        return toReportResponse(report);
    }

    @Transactional
    public void recordShare(UUID userId, ShareRequest request) {
        validateTargetExists(request.targetType(), request.targetId());

        ContentShare share = new ContentShare();
        share.setTargetType(request.targetType());
        share.setTargetId(request.targetId());
        share.setPlatform(request.platform().trim().toUpperCase());
        if (userId != null) {
            share.setUser(userRepository.getReferenceById(userId));
        }
        shareRepository.save(share);

        if (request.targetType() == ContentTargetType.PRODUCT) {
            adjustProductCounter(request.targetId(), "shares", 1);
        }
        log.info("Share recorded user={} target={}/{} platform={}",
                userId, request.targetType(), request.targetId(), request.platform());
    }

    private void rejectProductComments(ContentTargetType targetType) {
        if (targetType == ContentTargetType.PRODUCT) {
            throw new BusinessException(
                    "PRODUCT_REVIEWS_ONLY",
                    "Products use star ratings and reviews instead of comments.");
        }
    }

    private void validateTargetExists(ContentTargetType targetType, UUID targetId) {
        boolean exists = switch (targetType) {
            case POST -> contentPostRepository.findByIdAndIsPublicTrue(targetId).isPresent()
                    || contentPostRepository.findById(targetId).isPresent();
            case PRODUCT -> productRepository.findByIdAndIsPublishedTrue(targetId).isPresent()
                    || productRepository.findById(targetId).isPresent();
            case COMMENT -> commentRepository.findById(targetId).isPresent();
        };
        if (!exists) {
            throw new BusinessException("TARGET_NOT_FOUND",
                    targetType.name() + " not found: " + targetId);
        }
    }

    private UUID resolveCreatorId(ContentTargetType targetType, UUID targetId) {
        return switch (targetType) {
            case POST -> contentPostRepository.findById(targetId)
                    .map(p -> p.getCreator() != null ? p.getCreator().getId() : null)
                    .orElse(null);
            case PRODUCT -> productRepository.findById(targetId)
                    .map(p -> p.getCreator() != null ? p.getCreator().getId() : null)
                    .orElse(null);
            case COMMENT -> commentRepository.findById(targetId)
                    .map(c -> c.getUser() != null ? c.getUser().getId() : null)
                    .orElse(null);
        };
    }

    private void adjustReactionCount(ContentTargetType targetType, UUID targetId, ReactionType type, int delta) {
        if (targetType == ContentTargetType.COMMENT) {
            return;
        }
        if (targetType == ContentTargetType.POST) {
            if (type == ReactionType.LIKE) {
                contentPostRepository.findById(targetId).ifPresent(post -> {
                    post.setLikes(Math.max(0, post.getLikes() + delta));
                    contentPostRepository.save(post);
                });
            }
            return;
        }
        if (type == ReactionType.LIKE) {
            adjustProductCounter(targetId, "likes", delta);
        } else {
            adjustProductCounter(targetId, "dislikes", delta);
        }
    }

    private void adjustProductCounter(UUID productId, String field, int delta) {
        productRepository.findById(productId).ifPresent(product -> {
            switch (field) {
                case "likes" -> product.setLikes(Math.max(0, product.getLikes() + delta));
                case "dislikes" -> product.setDislikes(Math.max(0, product.getDislikes() + delta));
                case "favorites" -> product.setFavorites(Math.max(0, product.getFavorites() + delta));
                case "comments" -> product.setComments(Math.max(0, product.getComments() + delta));
                case "shares" -> product.setShares(Math.max(0, product.getShares() + delta));
                default -> throw new IllegalArgumentException("Unknown counter: " + field);
            }
            productRepository.save(product);
        });
    }

    private User requireUser(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found: " + userId));
    }

    private CommentResponse toCommentResponse(
            ContentComment comment,
            List<ContentComment> replies,
            Map<UUID, ReactionStats> reactionStats) {
        User author = comment.getUser();
        ReactionStats stats = reactionStats.getOrDefault(comment.getId(), new ReactionStats());
        List<CommentResponse> replyResponses = replies.stream()
                .map(reply -> toCommentResponse(reply, List.of(), reactionStats))
                .toList();

        return new CommentResponse(
                comment.getId(),
                comment.getTargetType(),
                comment.getTargetId(),
                author != null ? author.getId() : null,
                author != null ? author.getFullName() : null,
                author != null ? author.getAvatarUrl() : null,
                comment.getComment(),
                comment.getParent() != null ? comment.getParent().getId() : null,
                comment.getCreatedAt(),
                stats.likes,
                stats.dislikes,
                stats.userReaction,
                comment.getHiddenAt() != null,
                replyResponses
        );
    }

    private Map<UUID, ReactionStats> loadReactionStats(
            ContentTargetType targetType, List<UUID> targetIds, UUID viewerUserId) {
        if (targetIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, ReactionStats> stats = new HashMap<>();
        for (UUID targetId : targetIds) {
            stats.put(targetId, new ReactionStats());
        }

        List<ContentReaction> reactions = reactionRepository.findByTargetTypeAndTargetIdIn(targetType, targetIds);
        for (ContentReaction reaction : reactions) {
            ReactionStats entry = stats.computeIfAbsent(reaction.getTargetId(), ignored -> new ReactionStats());
            if (reaction.getType() == ReactionType.LIKE) {
                entry.likes++;
            } else {
                entry.dislikes++;
            }
            if (viewerUserId != null
                    && reaction.getUser() != null
                    && viewerUserId.equals(reaction.getUser().getId())) {
                entry.userReaction = reaction.getType();
            }
        }

        return stats;
    }

    private static final class ReactionStats {
        long likes;
        long dislikes;
        ReactionType userReaction;
    }

    public ReportResponse toReportResponse(ContentReport report) {
        return new ReportResponse(
                report.getId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getReporter() != null ? report.getReporter().getId() : null,
                report.getReason(),
                report.getDetails(),
                report.getStatus(),
                report.getAdminNotes(),
                report.getCreatedAt(),
                report.getUpdatedAt()
        );
    }
}
