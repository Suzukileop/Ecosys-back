package com.plateforme.marketplace.dto;

import com.plateforme.marketplace.entity.DeliveryMode;
import com.plateforme.marketplace.entity.DemoType;
import com.plateforme.marketplace.entity.ProductType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MarketplaceProductRequest(
        @NotNull
        ProductType type,

        @NotBlank
        @Size(max = 300)
        String title,

        @Size(max = 10000)
        String description,

        @NotNull
        @Min(0)
        Integer priceCents,

        @Size(max = 3)
        String currency,

        @Size(max = 100)
        String genre,

        @Size(max = 150)
        String specialite,

        @Size(max = 500)
        String thumbnailUrl,

        DemoType demoType,

        @Size(max = 500)
        String demoUrl,

        @Size(max = 2000)
        String demoDescription,

        List<String> demoSubtitles,

        List<ProductWhyBlock> whyProductBlocks,

        DeliveryMode deliveryMode,

        List<String> compatibleTools,

        @Size(max = 50)
        String fileFormat,

        Integer fileSizeMb,

        @Size(max = 10)
        String language,

        @Size(max = 20)
        String version,

        Integer previewLimitPercent,

        Integer maxDownloads,

        List<String> tags,

        Integer compareAtPriceCents,

        Integer videoDurationSeconds,

        @Size(max = 10)
        String videoResolution,

        Boolean isBestseller,

        Boolean isPublished,

        List<String> galleryImageUrls
) {
    public MarketplaceProductRequest {
        if (currency == null || currency.isBlank()) {
            currency = "EUR";
        }
        if (demoType == null) {
            demoType = DemoType.NONE;
        }
        if (deliveryMode == null) {
            deliveryMode = DeliveryMode.BOTH;
        }
        if (isPublished == null) {
            isPublished = Boolean.TRUE;
        }
        if (isBestseller == null) {
            isBestseller = Boolean.FALSE;
        }
        if (galleryImageUrls == null) {
            galleryImageUrls = List.of();
        }
    }
}
