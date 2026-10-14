package com.plateforme.marketplace.dto;

import com.plateforme.marketplace.entity.DeliveryMode;
import com.plateforme.marketplace.entity.ProductType;

import java.time.LocalDateTime;
import java.util.UUID;

public record OwnedProductResponse(
        UUID purchaseId,
        UUID productId,
        String productTitle,
        ProductType productType,
        String thumbnailUrl,
        LocalDateTime purchasedAt,
        int downloadCount,
        Integer maxDownloads,
        UUID creatorId,
        String creatorName,
        int pricePaidCents,
        String currency,
        String fileFormat,
        String genre,
        DeliveryMode deliveryMode,
        /** The creator's name for an {@code OTHER} type; null for every other type. */
        String productTypeLabel
) {}
