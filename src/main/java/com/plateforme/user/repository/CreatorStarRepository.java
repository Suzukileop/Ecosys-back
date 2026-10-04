package com.plateforme.user.repository;

import com.plateforme.user.entity.CreatorStar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreatorStarRepository extends JpaRepository<CreatorStar, UUID> {

    boolean existsByCreator_IdAndUser_Id(UUID creatorId, UUID userId);

    Optional<CreatorStar> findByCreator_IdAndUser_Id(UUID creatorId, UUID userId);

    long countByCreator_Id(UUID creatorId);
}
