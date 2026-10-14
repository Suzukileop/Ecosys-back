package com.plateforme.marketplace.entity;

/**
 * Merchandising status a creator can attach to a product, shown as badges on the product cards.
 * Stored by name in {@code marketplace_products.status_labels}; a product carries at most
 * {@code MarketplaceProductService.MAX_STATUS_LABELS} of them.
 */
public enum ProductStatusLabel {
    NEW,
    PROMOTION,
    CLEARANCE,
    POPULAR,
    TRENDING,
    LIMITED_EDITION,
    LAST_UNITS,
    EXCLUSIVE
}
