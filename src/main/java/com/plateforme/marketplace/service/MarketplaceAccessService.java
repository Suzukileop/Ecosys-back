package com.plateforme.marketplace.service;

import com.plateforme.marketplace.dto.AccessUrlResponse;
import com.plateforme.marketplace.dto.ProductPreviewResponse;
import com.plateforme.marketplace.entity.AccessMode;
import com.plateforme.marketplace.entity.ContentAccessLog;
import com.plateforme.marketplace.entity.DeliveryMode;
import com.plateforme.marketplace.entity.DemoType;
import com.plateforme.marketplace.entity.MarketplaceProduct;
import com.plateforme.marketplace.entity.MarketplacePurchase;
import com.plateforme.marketplace.entity.MarketplacePurchaseStatus;
import com.plateforme.marketplace.repository.ContentAccessLogRepository;
import com.plateforme.marketplace.repository.MarketplaceProductRepository;
import com.plateforme.marketplace.repository.MarketplacePurchaseRepository;
import com.plateforme.ecosystem.storage.StorageService;
import com.plateforme.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketplaceAccessService {

    private static final int STREAM_EXPIRY_MINUTES = 15;
    private static final int DOWNLOAD_EXPIRY_MINUTES = 10;

    private final MarketplacePurchaseService purchaseService;
    private final MarketplaceProductRepository productRepository;
    private final MarketplacePurchaseRepository purchaseRepository;
    private final ContentAccessLogRepository accessLogRepository;
    private final StorageService storageService;

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
        validateDownloadLimit(purchase, product, mode);

        int expiryMinutes = mode == AccessMode.STREAM ? STREAM_EXPIRY_MINUTES : DOWNLOAD_EXPIRY_MINUTES;
        String downloadFilename = resolveDownloadFilename(product);
        String signedUrl;
        try {
            if (mode == AccessMode.DOWNLOAD) {
                signedUrl = storageService.generateSignedDownloadUrl(
                        product.getMainFileR2Key(), downloadFilename, expiryMinutes);
            } else {
                signedUrl = storageService.generateSignedUrl(product.getMainFileR2Key(), expiryMinutes);
            }
        } catch (IOException e) {
            log.error("Failed to generate signed URL for product={} purchase={}", product.getId(), purchaseId, e);
            throw new BusinessException("ACCESS_URL_FAILED", "Failed to generate access URL");
        }

        if (mode == AccessMode.DOWNLOAD) {
            int downloads = purchase.getDownloadCount() != null ? purchase.getDownloadCount() : 0;
            purchase.setDownloadCount(downloads + 1);
            purchaseRepository.save(purchase);
            int productDownloads = product.getDownloads() != null ? product.getDownloads() : 0;
            product.setDownloads(productDownloads + 1);
            productRepository.save(product);
        }

        ContentAccessLog accessLog = new ContentAccessLog();
        accessLog.setPurchase(purchase);
        accessLog.setUser(purchase.getBuyer());
        accessLog.setProduct(product);
        accessLog.setAccessMode(mode);
        accessLog.setIpAddress(ipAddress);
        accessLog.setUserAgent(userAgent);
        accessLogRepository.save(accessLog);

        log.info("Access granted purchase={} product={} mode={}", purchaseId, product.getId(), mode);
        String responseFilename = mode == AccessMode.DOWNLOAD ? downloadFilename : null;
        return new AccessUrlResponse(signedUrl, mode, expiryMinutes, responseFilename);
    }

    private static String resolveDownloadFilename(MarketplaceProduct product) {
        String key = product.getMainFileR2Key();
        if (key != null && !key.isBlank()) {
            int slash = key.lastIndexOf('/');
            String leaf = slash >= 0 ? key.substring(slash + 1) : key;
            if (!leaf.isBlank()) {
                return leaf;
            }
        }
        String title = product.getTitle();
        if (title != null && !title.isBlank()) {
            return title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        }
        return "download";
    }

    @Transactional(readOnly = true)
    public ProductPreviewResponse getProductPreview(UUID productId) {
        MarketplaceProduct product = productRepository.findByIdAndIsPublishedTrue(productId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND",
                        "Published product not found: " + productId));

        String previewUrl = null;
        if (product.getDemoType() != DemoType.NONE && product.getDemoUrl() != null) {
            previewUrl = product.getDemoUrl();
        } else if (product.getPreviewLimitPercent() != null && product.getPreviewLimitPercent() > 0) {
            try {
                previewUrl = storageService.generateSignedUrl(product.getMainFileR2Key(), STREAM_EXPIRY_MINUTES);
            } catch (IOException e) {
                log.warn("Preview URL generation failed for product={}", productId, e);
            }
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

    private void validateDownloadLimit(MarketplacePurchase purchase, MarketplaceProduct product, AccessMode mode) {
        if (mode != AccessMode.DOWNLOAD || product.getMaxDownloads() == null) {
            return;
        }
        int count = purchase.getDownloadCount() != null ? purchase.getDownloadCount() : 0;
        if (count >= product.getMaxDownloads()) {
            throw new BusinessException("DOWNLOAD_LIMIT_REACHED",
                    "Maximum download count reached for this purchase");
        }
    }
}
