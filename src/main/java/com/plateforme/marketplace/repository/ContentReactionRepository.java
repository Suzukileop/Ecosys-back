package com.plateforme.marketplace.repository;

import com.plateforme.marketplace.entity.ContentReaction;
import com.plateforme.marketplace.entity.ContentTargetType;
import com.plateforme.marketplace.entity.ReactionType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContentReactionRepository extends JpaRepository<ContentReaction, UUID> {

    Optional<ContentReaction> findByUser_IdAndTargetTypeAndTargetId(
            UUID userId, ContentTargetType targetType, UUID targetId);

    long countByTargetTypeAndTargetIdAndType(
            ContentTargetType targetType, UUID targetId, ReactionType type);

    List<ContentReaction> findByTargetTypeAndTargetIdIn(
            ContentTargetType targetType, Collection<UUID> targetIds);

    /** Which of {@code targetIds} the user reacted to with {@code reactionType}. */
    @Query("""
            SELECT r.targetId FROM ContentReaction r
            WHERE r.user.id = :userId AND r.targetType = :targetType AND r.type = :reactionType
            AND r.targetId IN :targetIds
            """)
    List<UUID> findTargetIdsAmong(
            @Param("userId") UUID userId,
            @Param("targetType") ContentTargetType targetType,
            @Param("reactionType") ReactionType reactionType,
            @Param("targetIds") Collection<UUID> targetIds);

    @Query("""
            SELECT r.targetId FROM ContentReaction r
            WHERE r.user.id = :userId AND r.targetType = :targetType AND r.type = :reactionType
            ORDER BY r.createdAt DESC
            """)
    List<UUID> findRecentTargetIds(
            @Param("userId") UUID userId,
            @Param("targetType") ContentTargetType targetType,
            @Param("reactionType") ReactionType reactionType,
            Pageable pageable);
}
