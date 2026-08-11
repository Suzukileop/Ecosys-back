package com.plateforme.marketplace.repository;

import com.plateforme.marketplace.entity.MarketplaceBundleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MarketplaceBundleItemRepository extends JpaRepository<MarketplaceBundleItem, UUID> {

    List<MarketplaceBundleItem> findByBundle_IdOrderBySortOrderAsc(UUID bundleId);

    void deleteByBundle_Id(UUID bundleId);
}
