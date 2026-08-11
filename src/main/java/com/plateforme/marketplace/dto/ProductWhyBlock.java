package com.plateforme.marketplace.dto;

import java.util.List;
import java.util.UUID;

public record ProductWhyBlock(
        UUID id,
        int sortOrder,
        String mediaUrl,
        String mediaType,
        List<String> opinions
) {
    public ProductWhyBlock {
        opinions = opinions != null ? List.copyOf(opinions) : List.of();
    }
}
