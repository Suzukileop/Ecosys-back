package com.plateforme.marketplace.service;

import com.plateforme.marketplace.dto.MarketplaceProductRequest;
import com.plateforme.marketplace.dto.MarketplaceProductResponse;
import com.plateforme.marketplace.entity.ContentTargetType;
import com.plateforme.marketplace.entity.MarketplaceProduct;
import com.plateforme.marketplace.entity.ProductType;
import com.plateforme.marketplace.repository.MarketplaceProductRepository;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.UserRepository;
import com.plateforme.user.service.ProfileStoryFieldsSupport;
import com.plateforme.marketplace.service.ProductWhyBlocksSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketplaceProductService {

    private final MarketplaceProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional
    public MarketplaceProductResponse createProduct(UUID creatorId, MarketplaceProductRequest req) {
        User creator = requireCreator(creatorId);
        MarketplaceProduct product = new MarketplaceProduct();
        product.setCreator(creator);
        applyRequest(product, req);
        product = productRepository.save(product);
        log.info("Marketplace product created id={} creator={}", product.getId(), creatorId);
        return toResponse(product);
    }

    @Transactional
    public MarketplaceProductResponse updateProduct(UUID creatorId, UUID productId, MarketplaceProductRequest req) {
        MarketplaceProduct product = requireOwnedProduct(creatorId, productId);
        applyRequest(product, req);
        product = productRepository.save(product);
        log.info("Marketplace product updated id={} creator={}", productId, creatorId);
        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public MarketplaceProductResponse getMyProduct(UUID creatorId, UUID productId) {
        return toResponse(requireOwnedProduct(creatorId, productId));
    }

    @Transactional(readOnly = true)
    public Page<MarketplaceProductResponse> getMyProducts(UUID creatorId, Pageable pageable) {
        return productRepository.findByCreator_IdOrderByCreatedAtDesc(creatorId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public MarketplaceProductResponse getPublishedProduct(UUID productId) {
        MarketplaceProduct product = productRepository.findByIdAndIsPublishedTrue(productId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND",
                        "Published product not found: " + productId));
        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public Page<MarketplaceProductResponse> getPublishedProducts(
            UUID creatorId,
            ProductType type,
            String genre,
            String keyword,
            Integer minPriceCents,
            Integer maxPriceCents,
            UUID favoritesUserId,
            Pageable pageable) {
        String g = genre != null && !genre.isBlank() ? genre.trim() : null;
        String q = keyword != null && !keyword.isBlank() ? keyword.trim() : null;
        boolean freeOnly = minPriceCents != null && maxPriceCents != null
                && minPriceCents == 0 && maxPriceCents == 0;
        return productRepository.findPublishedFiltered(
                        creatorId,
                        type,
                        g,
                        q,
                        freeOnly,
                        freeOnly ? null : minPriceCents,
                        freeOnly ? null : maxPriceCents,
                        favoritesUserId,
                        ContentTargetType.PRODUCT,
                        pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<MarketplaceProductResponse> getSimilarProducts(UUID productId, int limit) {
        MarketplaceProduct source = requirePublishedProduct(productId);
        LinkedHashMap<UUID, MarketplaceProduct> ranked = new LinkedHashMap<>();
        Pageable pageable = PageRequest.of(0, Math.max(limit + 5, 10), Sort.by(Sort.Direction.DESC, "likes"));

        collectSimilarProducts(ranked, productId, limit, pageable, null, null, source.getGenre(), null);
        if (ranked.size() < limit && source.getTags() != null) {
            for (String rawTag : source.getTags()) {
                if (ranked.size() >= limit) {
                    break;
                }
                String tag = rawTag != null ? rawTag.trim() : "";
                if (tag.isEmpty()) {
                    continue;
                }
                collectSimilarProducts(ranked, productId, limit, pageable, null, null, null, tag);
            }
        }
        if (ranked.size() < limit && source.getSpecialite() != null && !source.getSpecialite().isBlank()) {
            collectSimilarProducts(ranked, productId, limit, pageable, null, null, null, source.getSpecialite().trim());
        }
        if (ranked.size() < limit) {
            collectSimilarProducts(
                    ranked, productId, limit, pageable, null, source.getType(), null, null);
        }

        return ranked.values().stream()
                .limit(limit)
                .map(this::toResponse)
                .toList();
    }

    private void collectSimilarProducts(
            LinkedHashMap<UUID, MarketplaceProduct> ranked,
            UUID excludeProductId,
            int limit,
            Pageable pageable,
            UUID creatorId,
            ProductType type,
            String genre,
            String keyword) {
        if (ranked.size() >= limit) {
            return;
        }
        String g = genre != null && !genre.isBlank() ? genre.trim() : null;
        String q = keyword != null && !keyword.isBlank() ? keyword.trim() : null;
        Page<MarketplaceProduct> page = productRepository.findPublishedFiltered(
                creatorId,
                type,
                g,
                q,
                false,
                null,
                null,
                null,
                ContentTargetType.PRODUCT,
                pageable);
        for (MarketplaceProduct candidate : page.getContent()) {
            if (candidate.getId().equals(excludeProductId)) {
                continue;
            }
            ranked.putIfAbsent(candidate.getId(), candidate);
            if (ranked.size() >= limit) {
                return;
            }
        }
    }

    @Transactional
    public void deleteProduct(UUID creatorId, UUID productId) {
        MarketplaceProduct product = requireOwnedProduct(creatorId, productId);
        product.setDeletedAt(LocalDateTime.now());
        product.setIsPublished(false);
        productRepository.save(product);
        log.info("Marketplace product soft-deleted id={} creator={}", productId, creatorId);
    }

    @Transactional
    public MarketplaceProductResponse setPublished(UUID creatorId, UUID productId, boolean published) {
        MarketplaceProduct product = requireOwnedProduct(creatorId, productId);
        product.setIsPublished(published);
        product = productRepository.save(product);
        log.info("Marketplace product id={} published={} by creator={}", productId, published, creatorId);
        return toResponse(product);
    }

    MarketplaceProduct requirePublishedProduct(UUID productId) {
        return productRepository.findByIdAndIsPublishedTrue(productId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND",
                        "Published product not found: " + productId));
    }

    MarketplaceProduct requireOwnedProduct(UUID creatorId, UUID productId) {
        MarketplaceProduct product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND",
                        "Product not found: " + productId));
        UUID ownerId = product.getCreator() != null ? product.getCreator().getId() : null;
        if (!Objects.equals(ownerId, creatorId)) {
            throw new AccessDeniedException("This product does not belong to the current user");
        }
        return product;
    }

    private User requireCreator(UUID creatorId) {
        return userRepository.findByIdAndDeletedAtIsNull(creatorId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND",
                        "User not found: " + creatorId));
    }

    private void applyRequest(MarketplaceProduct product, MarketplaceProductRequest req) {
        if (req.previewLimitPercent() != null
                && (req.previewLimitPercent() < 0 || req.previewLimitPercent() > 100)) {
            throw new BusinessException("INVALID_PREVIEW_LIMIT", "previewLimitPercent must be between 0 and 100");
        }

        if (req.compareAtPriceCents() != null && req.compareAtPriceCents() < req.priceCents()) {
            throw new BusinessException("INVALID_COMPARE_PRICE",
                    "Original price must be greater than or equal to the sale price.");
        }

        if (req.priceCents() == 0 && req.compareAtPriceCents() != null) {
            throw new BusinessException("INVALID_COMPARE_PRICE",
                    "Free products cannot have an original compare-at price.");
        }

        if (req.videoDurationSeconds() != null && req.videoDurationSeconds() <= 0) {
            throw new BusinessException("INVALID_VIDEO_DURATION", "videoDurationSeconds must be positive.");
        }

        List<String> tools = req.compatibleTools() != null ? req.compatibleTools() : List.of();
        List<String> tags = req.tags() != null ? req.tags() : List.of();
        List<String> galleryUrls = req.galleryImageUrls() != null
                ? req.galleryImageUrls().stream()
                    .filter(url -> url != null && !url.isBlank())
                    .map(String::trim)
                    .limit(12)
                    .toList()
                : List.of();

        product.setType(req.type());
        product.setTitle(req.title());
        product.setDescription(req.description());
        product.setPriceCents(req.priceCents());
        product.setCurrency(req.currency());
        product.setGenre(req.genre());
        product.setSpecialite(req.specialite());
        product.setThumbnailUrl(req.thumbnailUrl());
        product.setDemoType(req.demoType());
        product.setDemoUrl(req.demoUrl());
        List<String> demoSubtitles = ProfileStoryFieldsSupport.normalizeSubtitles(req.demoSubtitles());
        product.setDemoSubtitles(new ArrayList<>(demoSubtitles));
        product.setDemoDescription(
                !demoSubtitles.isEmpty()
                        ? demoSubtitles.get(0)
                        : req.demoDescription());
        if (req.whyProductBlocks() != null) {
            product.setWhyProductBlocks(new ArrayList<>(
                    ProductWhyBlocksSupport.normalizeBlocks(req.whyProductBlocks(), product.getCreator().getId())));
        }
        product.setDeliveryMode(req.deliveryMode());
        product.setCompatibleTools(new ArrayList<>(tools));
        product.setFileFormat(req.fileFormat());
        product.setFileSizeMb(req.fileSizeMb());
        product.setLanguage(req.language());
        product.setVersion(req.version());
        product.setPreviewLimitPercent(req.previewLimitPercent());
        product.setMaxDownloads(req.maxDownloads());
        product.setTags(new ArrayList<>(tags));
        product.setGalleryImageUrls(new ArrayList<>(galleryUrls));
        product.setCompareAtPriceCents(req.compareAtPriceCents());
        product.setVideoDurationSeconds(req.videoDurationSeconds());
        product.setVideoResolution(req.videoResolution());
        product.setIsBestseller(Boolean.TRUE.equals(req.isBestseller()));
        product.setIsPublished(Boolean.TRUE.equals(req.isPublished()));
    }

    MarketplaceProductResponse toResponse(MarketplaceProduct product) {
        User creator = product.getCreator();
        List<String> tools = product.getCompatibleTools() != null ? product.getCompatibleTools() : List.of();
        List<String> tags = product.getTags() != null ? product.getTags() : List.of();
        List<String> galleryUrls = product.getGalleryImageUrls() != null ? product.getGalleryImageUrls() : List.of();
        List<com.plateforme.marketplace.dto.ProductWhyBlock> whyBlocks = product.getWhyProductBlocks() != null
                ? product.getWhyProductBlocks() : List.of();
        List<String> demoSubtitles = resolveDemoSubtitles(product);

        return new MarketplaceProductResponse(
                product.getId(),
                creator.getId(),
                creator.getFullName(),
                creator.getAvatarUrl(),
                product.getType(),
                product.getTitle(),
                product.getDescription(),
                product.getPriceCents() != null ? product.getPriceCents() : 0,
                product.getCompareAtPriceCents(),
                product.getCurrency(),
                product.getGenre(),
                product.getSpecialite(),
                product.getThumbnailUrl(),
                product.getDemoType(),
                product.getDemoUrl(),
                product.getDemoDescription(),
                demoSubtitles,
                whyBlocks,
                product.getDeliveryMode(),
                tools,
                product.getFileFormat(),
                product.getFileSizeMb(),
                product.getLanguage(),
                product.getVersion(),
                product.getPreviewLimitPercent(),
                product.getMaxDownloads(),
                tags,
                product.getVideoDurationSeconds(),
                product.getVideoResolution(),
                Boolean.TRUE.equals(product.getIsBestseller()),
                product.getViews() != null ? product.getViews() : 0,
                product.getLikes() != null ? product.getLikes() : 0,
                product.getSalesCount() != null ? product.getSalesCount() : 0,
                product.getAverageRating(),
                product.getReviewCount() != null ? product.getReviewCount() : 0,
                Boolean.TRUE.equals(product.getIsPublished()),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                galleryUrls
        );
    }

    private static List<String> resolveDemoSubtitles(MarketplaceProduct product) {
        List<String> stored = product.getDemoSubtitles();
        if (stored != null && !stored.isEmpty()) {
            return stored;
        }
        String legacy = product.getDemoDescription();
        if (legacy != null && !legacy.isBlank()) {
            return List.of(legacy.trim());
        }
        return List.of();
    }
}
