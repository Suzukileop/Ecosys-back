package com.plateforme.marketplace.service;

import com.plateforme.marketplace.dto.AccessUrlResponse;
import com.plateforme.marketplace.dto.ProductPreviewResponse;
import com.plateforme.marketplace.entity.AccessMode;
import com.plateforme.marketplace.entity.DeliveryMode;
import com.plateforme.marketplace.entity.DemoType;
import com.plateforme.marketplace.entity.MarketplaceProduct;
import com.plateforme.marketplace.entity.MarketplacePurchase;
import com.plateforme.marketplace.entity.MarketplacePurchaseStatus;
import com.plateforme.marketplace.repository.MarketplaceProductRepository;
import com.plateforme.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketplaceAccessService {

    private final MarketplacePurchaseService purchaseService;
    private final MarketplaceProductRepository productRepository;

    @Transactional
    public AccessUrlResponse getAccessUrl(
            UUID buyerId,
            UUID purchaseId,
            AccessMode mode,
            String ipAddress,
            String userAgent) {
        MarketplacePurchase purchase = purchaseService.requireOwnedPurchase(buyerId, purchaseId);

        if (purchase.getPaymentStatus() != MarketplacePurchaseStatus.COMPLETED) {
            throw new BusinessException("PURCHASE_NOT_COMPLETED", "Purchase payment is not completed");
        }
        if (purchase.getExpiresAt() != null && purchase.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("PURCHASE_EXPIRED", "Purchase access has expired");
        }
        if (purchase.getProduct() == null) {
            throw new BusinessException("BUNDLE_ACCESS_UNSUPPORTED", "Bundle access is not yet supported");
        }

        MarketplaceProduct product = purchase.getProduct();
        validateDeliveryMode(product.getDeliveryMode(), mode);

        throw new BusinessException(
                "PRODUCT_FILE_UNAVAILABLE",
                "This product has no downloadable file."
        );
    }

    @Transactional(readOnly = true)
    public ProductPreviewResponse getProductPreview(UUID productId) {
        MarketplaceProduct product = productRepository.findByIdAndIsPublishedTrue(productId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND",
                        "Published product not found: " + productId));

        String previewUrl = null;
        if (product.getDemoType() != DemoType.NONE && product.getDemoUrl() != null) {
            previewUrl = product.getDemoUrl();
        }

        return new ProductPreviewResponse(
                previewUrl,
                product.getPreviewLimitPercent(),
                product.getDemoDescription()
        );
    }

    private void validateDeliveryMode(DeliveryMode deliveryMode, AccessMode mode) {
        if (deliveryMode == DeliveryMode.STREAM_ONLY && mode == AccessMode.DOWNLOAD) {
            throw new BusinessException("DOWNLOAD_NOT_ALLOWED", "This product is stream-only");
        }
        if (deliveryMode == DeliveryMode.DOWNLOAD && mode == AccessMode.STREAM) {
            throw new BusinessException("STREAM_NOT_ALLOWED", "This product is download-only");
        }
    }
}
