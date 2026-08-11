package com.plateforme.marketplace.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BundleRequest(
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

        @Size(max = 500)
        String thumbnailUrl,

        Integer discountPercent,

        @NotNull
        List<UUID> productIds,

        Boolean isPublished
) {
    public BundleRequest {
        if (currency == null || currency.isBlank()) {
            currency = "EUR";
        }
        if (isPublished == null) {
            isPublished = Boolean.FALSE;
        }
        if (productIds == null) {
            productIds = List.of();
        }
    }
}
