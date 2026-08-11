package com.plateforme.marketplace.dto;

import java.util.UUID;

public record ProductOwnershipResponse(
        boolean owned,
        UUID purchaseId,
        int downloadCount,
        Integer maxDownloads
) {
    public static ProductOwnershipResponse notOwned() {
        return new ProductOwnershipResponse(false, null, 0, null);
    }
}
