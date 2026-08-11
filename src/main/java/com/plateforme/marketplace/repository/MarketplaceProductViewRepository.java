package com.plateforme.marketplace.repository;

import com.plateforme.marketplace.entity.MarketplaceProductView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MarketplaceProductViewRepository extends JpaRepository<MarketplaceProductView, UUID> {

    boolean existsByProduct_IdAndUser_Id(UUID productId, UUID userId);
}
