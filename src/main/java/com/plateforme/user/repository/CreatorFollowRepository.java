package com.plateforme.user.repository;

import com.plateforme.user.entity.CreatorFollow;
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
public interface CreatorFollowRepository extends JpaRepository<CreatorFollow, UUID> {

    boolean existsByFollower_IdAndCreator_Id(UUID followerId, UUID creatorId);

    Optional<CreatorFollow> findByFollower_IdAndCreator_Id(UUID followerId, UUID creatorId);

    long countByCreator_Id(UUID creatorId);

    Page<CreatorFollow> findByFollower_IdOrderByCreatedAtDesc(UUID followerId, Pageable pageable);

    Page<CreatorFollow> findByCreator_IdOrderByCreatedAtDesc(UUID creatorId, Pageable pageable);

    @Query("""
            SELECT cf.creator.id FROM CreatorFollow cf
            WHERE cf.follower.id = :followerId AND cf.creator.id IN :creatorIds
            """)
    List<UUID> findFollowedCreatorIds(
            @Param("followerId") UUID followerId,
            @Param("creatorIds") Collection<UUID> creatorIds);

    @Query("""
            SELECT cf.creator.id, COUNT(cf) FROM CreatorFollow cf
            WHERE cf.creator.id IN :creatorIds
            GROUP BY cf.creator.id
            """)
    List<Object[]> countFollowersGrouped(@Param("creatorIds") Collection<UUID> creatorIds);
}
