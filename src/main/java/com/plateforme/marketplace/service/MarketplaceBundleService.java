package com.plateforme.marketplace.service;

import com.plateforme.marketplace.dto.BundleRequest;
import com.plateforme.marketplace.dto.BundleResponse;
import com.plateforme.marketplace.entity.MarketplaceBundle;
import com.plateforme.marketplace.entity.MarketplaceBundleItem;
import com.plateforme.marketplace.entity.MarketplaceProduct;
import com.plateforme.marketplace.repository.MarketplaceBundleItemRepository;
import com.plateforme.marketplace.repository.MarketplaceBundleRepository;
import com.plateforme.marketplace.repository.MarketplaceProductRepository;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketplaceBundleService {

    private final MarketplaceBundleRepository bundleRepository;
    private final MarketplaceBundleItemRepository bundleItemRepository;
    private final MarketplaceProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional
    public BundleResponse createBundle(UUID creatorId, BundleRequest req) {
        User creator = userRepository.findByIdAndDeletedAtIsNull(creatorId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found: " + creatorId));

        if (req.productIds().isEmpty()) {
            throw new BusinessException("BUNDLE_EMPTY", "Bundle must contain at least one product");
        }

        MarketplaceBundle bundle = new MarketplaceBundle();
        bundle.setCreator(creator);
        applyRequest(bundle, req);
        bundle = bundleRepository.save(bundle);
        replaceItems(bundle, creatorId, req.productIds());

        log.info("Marketplace bundle created id={} creator={}", bundle.getId(), creatorId);
        return toResponse(bundle);
    }

    @Transactional
    public BundleResponse updateBundle(UUID creatorId, UUID bundleId, BundleRequest req) {
        MarketplaceBundle bundle = requireOwnedBundle(creatorId, bundleId);
        if (req.productIds().isEmpty()) {
            throw new BusinessException("BUNDLE_EMPTY", "Bundle must contain at least one product");
        }
        applyRequest(bundle, req);
        bundle = bundleRepository.save(bundle);
        replaceItems(bundle, creatorId, req.productIds());
        log.info("Marketplace bundle updated id={} creator={}", bundleId, creatorId);
        return toResponse(bundle);
    }

    @Transactional(readOnly = true)
    public BundleResponse getMyBundle(UUID creatorId, UUID bundleId) {
        return toResponse(requireOwnedBundle(creatorId, bundleId));
    }

    @Transactional(readOnly = true)
    public Page<BundleResponse> getMyBundles(UUID creatorId, Pageable pageable) {
        return bundleRepository.findByCreator_IdOrderByCreatedAtDesc(creatorId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public void deleteBundle(UUID creatorId, UUID bundleId) {
        MarketplaceBundle bundle = requireOwnedBundle(creatorId, bundleId);
        bundle.setDeletedAt(LocalDateTime.now());
        bundle.setIsPublished(false);
        bundleRepository.save(bundle);
        log.info("Marketplace bundle soft-deleted id={} creator={}", bundleId, creatorId);
    }

    private void replaceItems(MarketplaceBundle bundle, UUID creatorId, List<UUID> productIds) {
        bundle.getItems().clear();
        bundleItemRepository.deleteByBundle_Id(bundle.getId());

        int order = 0;
        for (UUID productId : productIds) {
            MarketplaceProduct product = productRepository.findById(productId)
                    .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND",
                            "Product not found: " + productId));
            UUID ownerId = product.getCreator() != null ? product.getCreator().getId() : null;
            if (!Objects.equals(ownerId, creatorId)) {
                throw new AccessDeniedException("Product " + productId + " does not belong to the current user");
            }
            MarketplaceBundleItem item = new MarketplaceBundleItem();
            item.setBundle(bundle);
            item.setProduct(product);
            item.setSortOrder(order++);
            bundle.getItems().add(item);
        }
        bundleRepository.save(bundle);
    }

    private void applyRequest(MarketplaceBundle bundle, BundleRequest req) {
        if (req.discountPercent() != null
                && (req.discountPercent() < 0 || req.discountPercent() > 100)) {
            throw new BusinessException("INVALID_DISCOUNT", "discountPercent must be between 0 and 100");
        }
        bundle.setTitle(req.title());
        bundle.setDescription(req.description());
        bundle.setPriceCents(req.priceCents());
        bundle.setCurrency(req.currency());
        bundle.setThumbnailUrl(req.thumbnailUrl());
        bundle.setDiscountPercent(req.discountPercent());
        bundle.setIsPublished(Boolean.TRUE.equals(req.isPublished()));
    }

    private MarketplaceBundle requireOwnedBundle(UUID creatorId, UUID bundleId) {
        MarketplaceBundle bundle = bundleRepository.findById(bundleId)
                .orElseThrow(() -> new BusinessException("BUNDLE_NOT_FOUND",
                        "Bundle not found: " + bundleId));
        UUID ownerId = bundle.getCreator() != null ? bundle.getCreator().getId() : null;
        if (!Objects.equals(ownerId, creatorId)) {
            throw new AccessDeniedException("This bundle does not belong to the current user");
        }
        return bundle;
    }

    private BundleResponse toResponse(MarketplaceBundle bundle) {
        List<UUID> productIds = bundleItemRepository.findByBundle_IdOrderBySortOrderAsc(bundle.getId())
                .stream()
                .map(item -> item.getProduct().getId())
                .toList();

        return new BundleResponse(
                bundle.getId(),
                bundle.getCreator().getId(),
                bundle.getTitle(),
                bundle.getDescription(),
                bundle.getPriceCents() != null ? bundle.getPriceCents() : 0,
                bundle.getCurrency(),
                bundle.getThumbnailUrl(),
                bundle.getDiscountPercent(),
                Boolean.TRUE.equals(bundle.getIsPublished()),
                new ArrayList<>(productIds),
                bundle.getCreatedAt()
        );
    }
}
