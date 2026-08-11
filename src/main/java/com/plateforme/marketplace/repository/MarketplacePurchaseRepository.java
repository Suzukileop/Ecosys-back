package com.plateforme.marketplace.repository;

import com.plateforme.marketplace.entity.MarketplacePurchase;
import com.plateforme.marketplace.entity.MarketplacePurchaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MarketplacePurchaseRepository extends JpaRepository<MarketplacePurchase, UUID> {

    Page<MarketplacePurchase> findByBuyer_IdOrderByPurchasedAtDesc(UUID buyerId, Pageable pageable);

    boolean existsByBuyer_IdAndProduct_IdAndPaymentStatus(
            UUID buyerId, UUID productId, MarketplacePurchaseStatus paymentStatus);

    boolean existsByBuyer_IdAndBundle_IdAndPaymentStatus(
            UUID buyerId, UUID bundleId, MarketplacePurchaseStatus paymentStatus);

    Optional<MarketplacePurchase> findByIdAndBuyer_Id(UUID id, UUID buyerId);

    Optional<MarketplacePurchase> findFirstByBuyer_IdAndProduct_IdAndPaymentStatusOrderByPurchasedAtDesc(
            UUID buyerId, UUID productId, MarketplacePurchaseStatus paymentStatus);
}
