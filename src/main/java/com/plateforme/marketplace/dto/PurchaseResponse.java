package com.plateforme.marketplace.dto;

import com.plateforme.marketplace.entity.MarketplacePurchaseStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record PurchaseResponse(
        UUID id,
        UUID productId,
        UUID bundleId,
        int pricePaidCents,
        String currency,
        MarketplacePurchaseStatus paymentStatus,
        LocalDateTime purchasedAt,
        int downloadCount
) {}
