package com.plateforme.marketplace.service;

import com.plateforme.marketplace.dto.OwnedProductResponse;
import com.plateforme.marketplace.dto.ProductOwnershipResponse;
import com.plateforme.marketplace.dto.PurchaseResponse;
import com.plateforme.marketplace.entity.*;
import com.plateforme.marketplace.repository.MarketplaceBundleItemRepository;
import com.plateforme.marketplace.repository.MarketplaceBundleRepository;
import com.plateforme.marketplace.repository.MarketplacePurchaseRepository;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.shared.service.NotificationService;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketplacePurchaseService {

    private final MarketplacePurchaseRepository purchaseRepository;
    private final MarketplaceProductService productService;
    private final com.plateforme.marketplace.repository.MarketplaceProductRepository productRepository;
    private final MarketplaceBundleRepository bundleRepository;
    private final MarketplaceBundleItemRepository bundleItemRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public PurchaseResponse simulatePurchase(UUID buyerId, UUID productId) {
        User buyer = userRepository.findByIdAndDeletedAtIsNull(buyerId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found: " + buyerId));

        MarketplaceProduct product = productService.requirePublishedProduct(productId);

        if (purchaseRepository.existsByBuyer_IdAndProduct_IdAndPaymentStatus(
                buyerId, productId, MarketplacePurchaseStatus.COMPLETED)) {
            throw new BusinessException("ALREADY_PURCHASED", "You already own this product");
        }

        MarketplacePurchase purchase = new MarketplacePurchase();
        purchase.setBuyer(buyer);
        purchase.setProduct(product);
        purchase.setPricePaidCents(product.getPriceCents());
        purchase.setCurrency(product.getCurrency());
        purchase.setPaymentStatus(MarketplacePurchaseStatus.COMPLETED);
        purchase.setDownloadCount(0);

        purchase = purchaseRepository.save(purchase);
        incrementProductSales(product);
        notifyPurchase(buyer, product.getCreator(), product.getTitle(), purchase.getId(), productId);
        log.info("Simulated purchase id={} buyer={} product={}", purchase.getId(), buyerId, productId);
        return toPurchaseResponse(purchase);
    }

    @Transactional
    public PurchaseResponse simulateBundlePurchase(UUID buyerId, UUID bundleId) {
        User buyer = userRepository.findByIdAndDeletedAtIsNull(buyerId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found: " + buyerId));

        MarketplaceBundle bundle = bundleRepository.findByIdAndIsPublishedTrue(bundleId)
                .orElseThrow(() -> new BusinessException("BUNDLE_NOT_FOUND",
                        "Published bundle not found: " + bundleId));

        if (purchaseRepository.existsByBuyer_IdAndBundle_IdAndPaymentStatus(
                buyerId, bundleId, MarketplacePurchaseStatus.COMPLETED)) {
            throw new BusinessException("ALREADY_PURCHASED", "You already purchased this bundle");
        }

        MarketplacePurchase bundlePurchase = new MarketplacePurchase();
        bundlePurchase.setBuyer(buyer);
        bundlePurchase.setBundle(bundle);
        bundlePurchase.setPricePaidCents(bundle.getPriceCents() != null ? bundle.getPriceCents() : 0);
        bundlePurchase.setCurrency(bundle.getCurrency());
        bundlePurchase.setPaymentStatus(MarketplacePurchaseStatus.COMPLETED);
        bundlePurchase.setDownloadCount(0);
        bundlePurchase = purchaseRepository.save(bundlePurchase);

        List<MarketplaceBundleItem> items = bundleItemRepository.findByBundle_IdOrderBySortOrderAsc(bundleId);
        for (MarketplaceBundleItem item : items) {
            MarketplaceProduct product = item.getProduct();
            if (product == null) {
                continue;
            }
            UUID productId = product.getId();
            if (purchaseRepository.existsByBuyer_IdAndProduct_IdAndPaymentStatus(
                    buyerId, productId, MarketplacePurchaseStatus.COMPLETED)) {
                continue;
            }
            MarketplacePurchase productPurchase = new MarketplacePurchase();
            productPurchase.setBuyer(buyer);
            productPurchase.setProduct(product);
            productPurchase.setPricePaidCents(0);
            productPurchase.setCurrency(bundle.getCurrency());
            productPurchase.setPaymentStatus(MarketplacePurchaseStatus.COMPLETED);
            productPurchase.setDownloadCount(0);
            purchaseRepository.save(productPurchase);
            incrementProductSales(product);
        }

        User creator = bundle.getCreator();
        notifyPurchase(buyer, creator, bundle.getTitle(), bundlePurchase.getId(), bundleId);
        log.info("Simulated bundle purchase id={} buyer={} bundle={}", bundlePurchase.getId(), buyerId, bundleId);
        return toPurchaseResponse(bundlePurchase);
    }

    private void incrementProductSales(MarketplaceProduct product) {
        int current = product.getSalesCount() != null ? product.getSalesCount() : 0;
        product.setSalesCount(current + 1);
        productRepository.save(product);
    }

    private void notifyPurchase(User buyer, User creator, String itemTitle, UUID purchaseId, UUID refId) {
        notificationService.createAndSend(
                buyer.getId(),
                "MARKETPLACE_PURCHASE",
                "Purchase confirmed",
                "Your purchase of \"" + itemTitle + "\" is complete.",
                "PLATFORM",
                refId,
                purchaseId);

        if (creator != null && !creator.getId().equals(buyer.getId())) {
            notificationService.createAndSend(
                    creator.getId(),
                    "MARKETPLACE_SALE",
                    "New sale",
                    buyer.getFullName() + " purchased \"" + itemTitle + "\".",
                    "PLATFORM",
                    refId,
                    purchaseId);
        }
    }

    @Transactional(readOnly = true)
    public Page<OwnedProductResponse> getMyPurchases(UUID buyerId, Pageable pageable) {
        return purchaseRepository.findByBuyer_IdOrderByPurchasedAtDesc(buyerId, pageable)
                .map(this::toOwnedResponse);
    }

    @Transactional(readOnly = true)
    public ProductOwnershipResponse getProductOwnership(UUID buyerId, UUID productId) {
        return purchaseRepository
                .findFirstByBuyer_IdAndProduct_IdAndPaymentStatusOrderByPurchasedAtDesc(
                        buyerId, productId, MarketplacePurchaseStatus.COMPLETED)
                .map(purchase -> new ProductOwnershipResponse(
                        true,
                        purchase.getId(),
                        purchase.getDownloadCount() != null ? purchase.getDownloadCount() : 0,
                        purchase.getProduct() != null ? purchase.getProduct().getMaxDownloads() : null))
                .orElseGet(ProductOwnershipResponse::notOwned);
    }

    @Transactional(readOnly = true)
    public MarketplacePurchase requireOwnedPurchase(UUID buyerId, UUID purchaseId) {
        return purchaseRepository.findByIdAndBuyer_Id(purchaseId, buyerId)
                .orElseThrow(() -> new BusinessException("PURCHASE_NOT_FOUND",
                        "Purchase not found: " + purchaseId));
    }

    private PurchaseResponse toPurchaseResponse(MarketplacePurchase purchase) {
        return new PurchaseResponse(
                purchase.getId(),
                purchase.getProduct() != null ? purchase.getProduct().getId() : null,
                purchase.getBundle() != null ? purchase.getBundle().getId() : null,
                purchase.getPricePaidCents() != null ? purchase.getPricePaidCents() : 0,
                purchase.getCurrency(),
                purchase.getPaymentStatus(),
                purchase.getPurchasedAt(),
                purchase.getDownloadCount() != null ? purchase.getDownloadCount() : 0
        );
    }

    private OwnedProductResponse toOwnedResponse(MarketplacePurchase purchase) {
        if (purchase.getProduct() == null) {
            throw new BusinessException("PURCHASE_INVALID", "Bundle purchases are not yet supported in library view");
        }
        MarketplaceProduct product = purchase.getProduct();
        return new OwnedProductResponse(
                purchase.getId(),
                product.getId(),
                product.getTitle(),
                product.getType(),
                product.getThumbnailUrl(),
                purchase.getPurchasedAt(),
                purchase.getDownloadCount() != null ? purchase.getDownloadCount() : 0,
                product.getMaxDownloads(),
                product.getCreator().getId(),
                product.getCreator().getFullName(),
                purchase.getPricePaidCents() != null ? purchase.getPricePaidCents() : 0,
                purchase.getCurrency(),
                product.getFileFormat(),
                product.getGenre(),
                product.getDeliveryMode()
        );
    }
}
