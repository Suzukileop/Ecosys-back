package com.plateforme.user.repository;

import com.plateforme.user.entity.CreatorStar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreatorStarRepository extends JpaRepository<CreatorStar, UUID> {

    boolean existsByCreator_IdAndUser_Id(UUID creatorId, UUID userId);

    Optional<CreatorStar> findByCreator_IdAndUser_Id(UUID creatorId, UUID userId);

    long countByCreator_Id(UUID creatorId);

    /** Grouped count for a page of creator cards: [creatorId, count] rows, creators with none omitted. */
    @Query("SELECT s.creator.id, COUNT(s) FROM CreatorStar s WHERE s.creator.id IN :creatorIds GROUP BY s.creator.id")
    List<Object[]> countByCreatorIds(@Param("creatorIds") Collection<UUID> creatorIds);
}
