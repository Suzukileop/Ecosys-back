package com.plateforme.marketplace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ProductGroupRequest(
        @NotBlank
        @Size(max = 120)
        String name,

        List<UUID> productIds,

        Integer sortOrder
) {
    public ProductGroupRequest {
        if (productIds == null) {
            productIds = List.of();
        }
        if (sortOrder == null) {
            sortOrder = 0;
        }
    }
}
