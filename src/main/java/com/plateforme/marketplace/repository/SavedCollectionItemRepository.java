package com.plateforme.marketplace.repository;

import com.plateforme.marketplace.entity.SavedCollectionItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface SavedCollectionItemRepository extends JpaRepository<SavedCollectionItem, UUID> {

    boolean existsByCollection_IdAndPostId(UUID collectionId, UUID postId);

    @Modifying
    @Query("DELETE FROM SavedCollectionItem i WHERE i.collection.id = :collectionId AND i.postId = :postId")
    int deleteFromCollection(@Param("collectionId") UUID collectionId, @Param("postId") UUID postId);

    /** Un-saving a post takes it out of every collection of that user. */
    @Modifying
    @Query("""
            DELETE FROM SavedCollectionItem i
            WHERE i.postId = :postId
            AND i.collection.id IN (SELECT c.id FROM SavedCollection c WHERE c.user.id = :userId)
            """)
    int deleteForUserAndPost(@Param("userId") UUID userId, @Param("postId") UUID postId);

    /**
     * Visible items per collection — public, live posts only, the same rule as the saved list
     * (a post made private or archived disappears from counts without being unfiled).
     */
    @Query("""
            SELECT i.collection.id, COUNT(i) FROM SavedCollectionItem i, ContentPost cp
            WHERE i.collection.id IN :collectionIds AND cp.id = i.postId
            AND cp.isPublic = true AND cp.archivedAt IS NULL
            GROUP BY i.collection.id
            """)
    List<Object[]> countVisibleByCollectionIds(@Param("collectionIds") Collection<UUID> collectionIds);

    /** Most recently filed visible posts of a collection — its cover mosaic. */
    @Query("""
            SELECT cp.mediaUrl, cp.mediaType FROM SavedCollectionItem i, ContentPost cp
            WHERE i.collection.id = :collectionId AND cp.id = i.postId
            AND cp.isPublic = true AND cp.archivedAt IS NULL AND cp.mediaUrl IS NOT NULL
            ORDER BY i.addedAt DESC
            """)
    List<Object[]> findCoverMedia(@Param("collectionId") UUID collectionId, Pageable pageable);

    /** Collections of this user that hold the post. */
    @Query("""
            SELECT i.collection.id FROM SavedCollectionItem i
            WHERE i.postId = :postId AND i.collection.user.id = :userId
            """)
    List<UUID> findCollectionIdsForPost(@Param("userId") UUID userId, @Param("postId") UUID postId);
}
