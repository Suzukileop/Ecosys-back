package com.plateforme.marketplace.repository;

import com.plateforme.marketplace.entity.MarketplaceProductGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MarketplaceProductGroupRepository extends JpaRepository<MarketplaceProductGroup, UUID> {

    Page<MarketplaceProductGroup> findByCreator_IdOrderBySortOrderAscCreatedAtAsc(
            UUID creatorId,
            Pageable pageable
    );

    boolean existsByCreator_IdAndNameIgnoreCase(UUID creatorId, String name);

    boolean existsByCreator_IdAndNameIgnoreCaseAndIdNot(UUID creatorId, String name, UUID id);

    Optional<MarketplaceProductGroup> findByIdAndCreator_Id(UUID id, UUID creatorId);
}
