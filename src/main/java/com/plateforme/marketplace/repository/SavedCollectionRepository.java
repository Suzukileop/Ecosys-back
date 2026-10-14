package com.plateforme.marketplace.repository;

import com.plateforme.marketplace.entity.SavedCollection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SavedCollectionRepository extends JpaRepository<SavedCollection, UUID> {

    List<SavedCollection> findByUser_IdOrderByUpdatedAtDesc(UUID userId);

    Optional<SavedCollection> findByIdAndUser_Id(UUID id, UUID userId);

    boolean existsByUser_IdAndNameIgnoreCase(UUID userId, String name);

    boolean existsByUser_IdAndNameIgnoreCaseAndIdNot(UUID userId, String name, UUID id);

    long countByUser_Id(UUID userId);
}
