package com.plateforme.marketplace.repository;

import com.plateforme.marketplace.entity.MarketplaceProductGroupItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MarketplaceProductGroupItemRepository extends JpaRepository<MarketplaceProductGroupItem, UUID> {

    List<MarketplaceProductGroupItem> findByProductGroup_IdOrderBySortOrderAsc(UUID groupId);

    void deleteByProductGroup_Id(UUID groupId);
}
