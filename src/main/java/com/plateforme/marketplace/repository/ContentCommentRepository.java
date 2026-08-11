package com.plateforme.marketplace.repository;

import com.plateforme.marketplace.entity.ContentComment;
import com.plateforme.marketplace.entity.ContentTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ContentCommentRepository extends JpaRepository<ContentComment, UUID> {

    @Query("""
            SELECT c FROM ContentComment c
            WHERE c.targetType = :targetType
              AND c.targetId = :targetId
              AND c.parent IS NULL
              AND c.deletedAt IS NULL
              AND (:includeHidden = true OR c.hiddenAt IS NULL)
            ORDER BY c.createdAt DESC
            """)
    Page<ContentComment> findTopLevelVisible(
            @Param("targetType") ContentTargetType targetType,
            @Param("targetId") UUID targetId,
            @Param("includeHidden") boolean includeHidden,
            Pageable pageable);

    @Query("""
            SELECT c FROM ContentComment c
            WHERE c.parent.id IN :parentIds
              AND c.deletedAt IS NULL
              AND (:includeHidden = true OR c.hiddenAt IS NULL)
            ORDER BY c.createdAt ASC
            """)
    List<ContentComment> findRepliesVisible(
            @Param("parentIds") Collection<UUID> parentIds,
            @Param("includeHidden") boolean includeHidden);

    long countByTargetTypeAndTargetIdAndDeletedAtIsNullAndHiddenAtIsNull(
            ContentTargetType targetType, UUID targetId);
}
