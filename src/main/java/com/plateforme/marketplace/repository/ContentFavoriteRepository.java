package com.plateforme.marketplace.repository;

import com.plateforme.marketplace.entity.ContentFavorite;
import com.plateforme.marketplace.entity.ContentTargetType;
import org.springframework.data.domain.Page;
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
public interface ContentFavoriteRepository extends JpaRepository<ContentFavorite, UUID> {

    Optional<ContentFavorite> findByUser_IdAndTargetTypeAndTargetId(
            UUID userId, ContentTargetType targetType, UUID targetId);

    Page<ContentFavorite> findByUser_IdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** Which of {@code targetIds} the user has favorited. */
    @Query("""
            SELECT f.targetId FROM ContentFavorite f
            WHERE f.user.id = :userId AND f.targetType = :targetType AND f.targetId IN :targetIds
            """)
    List<UUID> findTargetIdsAmong(
            @Param("userId") UUID userId,
            @Param("targetType") ContentTargetType targetType,
            @Param("targetIds") Collection<UUID> targetIds);

    @Query("""
            SELECT f.targetId FROM ContentFavorite f
            WHERE f.user.id = :userId AND f.targetType = :targetType
            ORDER BY f.createdAt DESC
            """)
    List<UUID> findRecentTargetIds(
            @Param("userId") UUID userId, @Param("targetType") ContentTargetType targetType, Pageable pageable);

    long countByTargetTypeAndTargetId(ContentTargetType targetType, UUID targetId);
}
