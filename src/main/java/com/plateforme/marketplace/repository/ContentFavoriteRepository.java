package com.plateforme.marketplace.repository;

import com.plateforme.marketplace.entity.ContentFavorite;
import com.plateforme.marketplace.entity.ContentTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContentFavoriteRepository extends JpaRepository<ContentFavorite, UUID> {

    Optional<ContentFavorite> findByUser_IdAndTargetTypeAndTargetId(
            UUID userId, ContentTargetType targetType, UUID targetId);

    Page<ContentFavorite> findByUser_IdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Query("SELECT f.targetId FROM ContentFavorite f WHERE f.user.id = :userId AND f.targetType = :targetType")
    List<UUID> findTargetIdsByUser_IdAndTargetType(
            @Param("userId") UUID userId, @Param("targetType") ContentTargetType targetType);

    long countByTargetTypeAndTargetId(ContentTargetType targetType, UUID targetId);
}
