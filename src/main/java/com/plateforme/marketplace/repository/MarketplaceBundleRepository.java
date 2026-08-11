package com.plateforme.marketplace.repository;

import com.plateforme.marketplace.entity.MarketplaceBundle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MarketplaceBundleRepository extends JpaRepository<MarketplaceBundle, UUID> {

    Page<MarketplaceBundle> findByCreator_IdOrderByCreatedAtDesc(UUID creatorId, Pageable pageable);

    Optional<MarketplaceBundle> findByIdAndIsPublishedTrue(UUID id);
}
