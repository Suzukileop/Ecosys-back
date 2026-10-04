package com.plateforme.marketplace.service;

import com.plateforme.marketplace.dto.MarketplaceProductRequest;
import com.plateforme.marketplace.dto.MarketplaceProductResponse;
import com.plateforme.marketplace.entity.ContentTargetType;
import com.plateforme.marketplace.entity.MarketplaceProduct;
import com.plateforme.marketplace.entity.ProductType;
import com.plateforme.marketplace.repository.MarketplaceProductGroupItemRepository;
import com.plateforme.marketplace.repository.MarketplaceProductRepository;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.entity.CreatorProfile;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.CreatorProfileRepository;
import com.plateforme.user.repository.UserRepository;
import com.plateforme.user.service.CreatorProfileReadinessService;
import com.plateforme.user.service.FollowerPublishNotifyService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketplaceProductService {

    private final MarketplaceProductRepository productRepository;
    private final MarketplaceProductGroupItemRepository productGroupItemRepository;
    private final UserRepository userRepository;
    private final CreatorProfileRepository creatorProfileRepository;
    private final CreatorProfileReadinessService creatorProfileReadinessService;
    private final FollowerPublishNotifyService followerPublishNotifyService;
    private final ProductBestsellerService bestsellerService;

    @Transactional
    public MarketplaceProductResponse createProduct(UUID creatorId, MarketplaceProductRequest req) {
        User creator = requireCreator(creatorId);
        creatorProfileReadinessService.requireReadyForProducts(creatorId);
        MarketplaceProduct product = new MarketplaceProduct();
        product.setCreator(creator);
        applyRequest(product, req);
        product = productRepository.save(product);
        log.info("Marketplace product created id={} creator={}", product.getId(), creatorId);
        if (Boolean.TRUE.equals(product.getIsPublished())) {
            followerPublishNotifyService.notifyFollowersNewProduct(
                    creatorId, product.getId(), product.getTitle());
        }
        return toResponse(product);
    }

    @Transactional
    public MarketplaceProductResponse updateProduct(UUID creatorId, UUID productId, MarketplaceProductRequest req) {
        MarketplaceProduct product = requireOwnedProduct(creatorId, productId);
        boolean wasPublished = Boolean.TRUE.equals(product.getIsPublished());
        applyRequest(product, req);
        product = productRepository.save(product);
        log.info("Marketplace product updated id={} creator={}", productId, creatorId);
        if (!wasPublished && Boolean.TRUE.equals(product.getIsPublished())) {
            followerPublishNotifyService.notifyFollowersNewProduct(
                    creatorId, product.getId(), product.getTitle());
        }
        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public MarketplaceProductResponse getMyProduct(UUID creatorId, UUID productId) {
        return toResponse(requireOwnedProduct(creatorId, productId));
    }

    @Transactional(readOnly = true)
    public Page<MarketplaceProductResponse> getMyProducts(UUID creatorId, Pageable pageable) {
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<MarketplaceProduct> page = productRepository.findByCreatorIdPinnedFirst(creatorId, unsorted);
        return mapPage(page);
    }

    @Transactional(readOnly = true)
    public MarketplaceProductResponse getPublishedProduct(UUID productId) {
        MarketplaceProduct product = productRepository.findByIdAndIsPublishedTrue(productId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND",
                        "Product not found."));
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
            String format,
            boolean profileOnly,
            Pageable pageable) {
        String g = genre != null && !genre.isBlank() ? genre.trim() : null;
        String stripped = keyword != null ? keyword.trim().replaceFirst("^#+", "").trim() : null;
        String q = stripped != null && !stripped.isEmpty() ? stripped : null;
        boolean freeOnly = minPriceCents != null && maxPriceCents != null
                && minPriceCents == 0 && maxPriceCents == 0;
        boolean physicalOnly = "physical".equalsIgnoreCase(format);
        boolean virtualOnly = "virtual".equalsIgnoreCase(format);
        Page<MarketplaceProduct> page = productRepository.findPublishedFiltered(
                        creatorId,
                        physicalOnly ? ProductType.PHYSICAL : type,
                        g,
                        q,
                        freeOnly,
                        freeOnly ? null : minPriceCents,
                        freeOnly ? null : maxPriceCents,
                        favoritesUserId,
                        ContentTargetType.PRODUCT,
                        physicalOnly,
                        virtualOnly,
                        profileOnly,
                        pageable);
        return mapPage(page);
    }

    /**
     * Search shortcuts ranked by engagement of the published products that carry them (tags, genre,
     * specialty, short titles) — the catalog has no search log, so demand is inferred from views,
     * likes and sales.
     */
    @Transactional(readOnly = true)
    public List<String> getPopularSearchTerms(String format, int limit) {
        boolean physicalOnly = "physical".equalsIgnoreCase(format);
        boolean virtualOnly = "virtual".equalsIgnoreCase(format);
        Page<MarketplaceProduct> page = productRepository.findPublishedFiltered(
                null, physicalOnly ? ProductType.PHYSICAL : null, null, null, false, null, null,
                null, ContentTargetType.PRODUCT, physicalOnly, virtualOnly, false,
                PageRequest.of(0, POPULAR_TERMS_SAMPLE_SIZE, Sort.by(Sort.Direction.DESC, "views")));

        Map<String, Long> scores = new HashMap<>();
        Map<String, String> labels = new HashMap<>();
        for (MarketplaceProduct product : page.getContent()) {
            long weight = 1L
                    + nonNull(product.getViews())
                    + 3L * nonNull(product.getLikes())
                    + 5L * nonNull(product.getSalesCount());
            java.util.Set<String> seen = new java.util.HashSet<>();
            List<String> candidates = new ArrayList<>();
            if (product.getTags() != null) candidates.addAll(product.getTags());
            candidates.add(product.getGenre());
            candidates.add(product.getSpecialite());
            candidates.add(searchableTitle(product.getTitle()));
            for (String raw : candidates) {
                String label = normalizeSearchTerm(raw);
                if (label == null) continue;
                String key = label.toLowerCase(java.util.Locale.ROOT);
                if (!seen.add(key)) continue;
                scores.merge(key, weight, Long::sum);
                labels.putIfAbsent(key, label);
            }
        }
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(Math.max(1, limit))
                .map(entry -> labels.get(entry.getKey()))
                .toList();
    }

    private static final int POPULAR_TERMS_SAMPLE_SIZE = 300;
    /** A physical product listed without a quantity is a single unit. */
    private static final int DEFAULT_PHYSICAL_STOCK = 1;

    private static long nonNull(Integer value) {
        return value != null ? value : 0L;
    }

    /** Keeps short, brand-like titles ("Ferrari", "Cargo pants") and drops trailing versions ("pants 4.0"). */
    private static String searchableTitle(String title) {
        if (title == null) return null;
        List<String> words = new ArrayList<>(List.of(title.trim().split("\\s+")));
        while (!words.isEmpty() && words.get(words.size() - 1).matches(".*\\d.*")) {
            words.remove(words.size() - 1);
        }
        return words.isEmpty() || words.size() > 3 ? null : String.join(" ", words);
    }

    private static String normalizeSearchTerm(String raw) {
        if (raw == null) return null;
        String collapsed = raw.trim().replaceAll("\\s+", " ");
        if (collapsed.length() < 2 || collapsed.length() > 30) return null;
        StringBuilder out = new StringBuilder(collapsed.length());
        for (String word : collapsed.split(" ")) {
            if (!out.isEmpty()) out.append(' ');
            out.append(word.substring(0, 1).toUpperCase(java.util.Locale.ROOT))
                    .append(word.substring(1).toLowerCase(java.util.Locale.ROOT));
        }
        return out.toString();
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

        List<MarketplaceProduct> products = ranked.values().stream().limit(limit).toList();
        Map<UUID, String> shopNames = loadShopNames(products);
        ProductBestsellerService.Rankings rankings = rankingsFor(products);
        return products.stream()
                .map(product -> toResponse(product, shopNames.get(product.getCreator().getId()), rankings))
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
                false,
                false,
                false,
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
        productGroupItemRepository.deleteByProduct_Id(productId);
        log.info("Marketplace product soft-deleted id={} creator={}", productId, creatorId);
    }

    @Transactional
    public MarketplaceProductResponse setPublished(UUID creatorId, UUID productId, boolean published) {
        MarketplaceProduct product = requireOwnedProduct(creatorId, productId);
        boolean wasPublished = Boolean.TRUE.equals(product.getIsPublished());
        if (published) {
            creatorProfileReadinessService.requireReadyForProducts(creatorId);
        }
        product.setIsPublished(published);
        product = productRepository.save(product);
        log.info("Marketplace product id={} published={} by creator={}", productId, published, creatorId);
        if (published && !wasPublished) {
            followerPublishNotifyService.notifyFollowersNewProduct(
                    creatorId, product.getId(), product.getTitle());
        }
        return toResponse(product);
    }

    @Transactional
    public MarketplaceProductResponse recordSale(UUID creatorId, UUID productId, int quantity) {
        MarketplaceProduct product = requireOwnedProduct(creatorId, productId);
        if (quantity < 1) {
            throw new BusinessException("INVALID_QUANTITY", "Quantity must be at least 1.");
        }
        consumeStock(product, quantity);
        int sales = product.getSalesCount() != null ? product.getSalesCount() : 0;
        product.setSalesCount(sales + quantity);
        product = productRepository.save(product);
        log.info("Marketplace product id={} sale recorded qty={} by creator={}", productId, quantity, creatorId);
        return toResponse(product);
    }

    @Transactional
    public MarketplaceProductResponse undoSale(UUID creatorId, UUID productId, int quantity) {
        MarketplaceProduct product = requireOwnedProduct(creatorId, productId);
        int sales = product.getSalesCount() != null ? product.getSalesCount() : 0;
        if (quantity < 1 || quantity > sales) {
            throw new BusinessException("INVALID_QUANTITY", "There are not that many sales to undo.");
        }
        product.setSalesCount(sales - quantity);
        if (product.getType() == ProductType.PHYSICAL) {
            int stock = product.getStockQuantity() != null ? product.getStockQuantity() : DEFAULT_PHYSICAL_STOCK;
            product.setStockQuantity(Math.max(stock, 0) + quantity);
        }
        product = productRepository.save(product);
        log.info("Marketplace product id={} sale undone qty={} by creator={}", productId, quantity, creatorId);
        return toResponse(product);
    }

    /** Removes {@code quantity} units from a physical product's stock, rejecting oversells. */
    public void consumeStock(MarketplaceProduct product, int quantity) {
        if (product.getType() != ProductType.PHYSICAL) {
            return;
        }
        int stock = product.getStockQuantity() != null ? product.getStockQuantity() : DEFAULT_PHYSICAL_STOCK;
        if (stock <= 0) {
            throw new BusinessException("OUT_OF_STOCK", "This product is out of stock.");
        }
        if (quantity > stock) {
            throw new BusinessException("INSUFFICIENT_STOCK",
                    stock == 1 ? "Only 1 unit is left in stock." : "Only " + stock + " units are left in stock.");
        }
        product.setStockQuantity(stock - quantity);
    }

    @Transactional
    public MarketplaceProductResponse setPinned(UUID creatorId, UUID productId, boolean pinned) {
        MarketplaceProduct product = requireOwnedProduct(creatorId, productId);
        product.setPinnedAt(pinned ? LocalDateTime.now() : null);
        product = productRepository.save(product);
        log.info("Marketplace product id={} pinned={} by creator={}", productId, pinned, creatorId);
        return toResponse(product);
    }

    @Transactional
    public MarketplaceProductResponse setShowOnProfile(UUID creatorId, UUID productId, boolean showOnProfile) {
        MarketplaceProduct product = requireOwnedProduct(creatorId, productId);
        product.setShowOnProfile(showOnProfile);
        product = productRepository.save(product);
        log.info("Marketplace product id={} showOnProfile={} by creator={}", productId, showOnProfile, creatorId);
        return toResponse(product);
    }

    MarketplaceProduct requirePublishedProduct(UUID productId) {
        return productRepository.findByIdAndIsPublishedTrue(productId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND",
                        "Product not found."));
    }

    MarketplaceProduct requireOwnedProduct(UUID creatorId, UUID productId) {
        MarketplaceProduct product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND",
                        "Product not found."));
        UUID ownerId = product.getCreator() != null ? product.getCreator().getId() : null;
        if (!Objects.equals(ownerId, creatorId)) {
            throw new AccessDeniedException("This product does not belong to the current user");
        }
        return product;
    }

    private User requireCreator(UUID creatorId) {
        return userRepository.findByIdAndDeletedAtIsNull(creatorId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND",
                        "User not found."));
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
        List<String> tags = normalizeHashtags(req.tags());
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
        product.setIsPublished(Boolean.TRUE.equals(req.isPublished()));
        product.setStockQuantity(req.type() == ProductType.PHYSICAL
                ? (req.stockQuantity() != null ? req.stockQuantity() : DEFAULT_PHYSICAL_STOCK)
                : null);
    }

    static final int MAX_HASHTAGS = 15;
    static final int MAX_HASHTAG_LENGTH = 40;

    /**
     * Stored without the leading {@code #}; letters (any script), digits, {@code _} and {@code -} only,
     * de-duplicated case-insensitively in first-seen order.
     */
    static List<String> normalizeHashtags(List<String> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        java.util.LinkedHashMap<String, String> unique = new java.util.LinkedHashMap<>();
        for (String value : raw) {
            if (value == null) continue;
            String cleaned = value.trim().replaceAll("^#+", "").replaceAll("[^\\p{L}\\p{N}_-]", "");
            if (cleaned.isEmpty()) continue;
            if (cleaned.length() > MAX_HASHTAG_LENGTH) cleaned = cleaned.substring(0, MAX_HASHTAG_LENGTH);
            unique.putIfAbsent(cleaned.toLowerCase(java.util.Locale.ROOT), cleaned);
            if (unique.size() >= MAX_HASHTAGS) break;
        }
        return List.copyOf(unique.values());
    }

    MarketplaceProductResponse toResponse(MarketplaceProduct product) {
        String shopName = null;
        if (product.getCreator() != null && product.getCreator().getId() != null) {
            shopName = loadShopNames(List.of(product)).get(product.getCreator().getId());
        }
        return toResponse(product, shopName, rankingsFor(List.of(product)));
    }

    private Page<MarketplaceProductResponse> mapPage(Page<MarketplaceProduct> page) {
        Map<UUID, String> shopNames = loadShopNames(page.getContent());
        ProductBestsellerService.Rankings rankings = rankingsFor(page.getContent());
        return page.map(product -> toResponse(product, shopNames.get(product.getCreator().getId()), rankings));
    }

    private ProductBestsellerService.Rankings rankingsFor(List<MarketplaceProduct> products) {
        List<UUID> creatorIds = products.stream()
                .map(MarketplaceProduct::getCreator)
                .filter(Objects::nonNull)
                .map(User::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return bestsellerService.rank(creatorIds);
    }

    MarketplaceProductResponse toResponse(
            MarketplaceProduct product, String shopName, ProductBestsellerService.Rankings rankings) {
        Integer bestsellerRank = rankings.shopRank(product.getId());
        User creator = product.getCreator();
        List<String> tools = product.getCompatibleTools() != null ? product.getCompatibleTools() : List.of();
        List<String> tags = product.getTags() != null ? product.getTags() : List.of();
        List<String> galleryUrls = product.getGalleryImageUrls() != null ? product.getGalleryImageUrls() : List.of();
        List<com.plateforme.marketplace.dto.ProductWhyBlock> whyBlocks = product.getWhyProductBlocks() != null
                ? product.getWhyProductBlocks() : List.of();
        List<String> demoSubtitles = resolveDemoSubtitles(product);
        String authorName = creator.getFullName();
        String resolvedShopName = (shopName != null && !shopName.isBlank()) ? shopName.trim() : null;

        return new MarketplaceProductResponse(
                product.getId(),
                creator.getId(),
                authorName,
                creator.getAvatarUrl(),
                resolvedShopName,
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
                bestsellerRank != null,
                product.getPinnedAt() != null,
                product.getViews() != null ? product.getViews() : 0,
                product.getLikes() != null ? product.getLikes() : 0,
                product.getSalesCount() != null ? product.getSalesCount() : 0,
                product.getAverageRating(),
                product.getReviewCount() != null ? product.getReviewCount() : 0,
                Boolean.TRUE.equals(product.getIsPublished()),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                galleryUrls,
                product.getType() == ProductType.PHYSICAL
                        ? (product.getStockQuantity() != null ? product.getStockQuantity() : DEFAULT_PHYSICAL_STOCK)
                        : null,
                !Boolean.FALSE.equals(product.getShowOnProfile()),
                bestsellerRank,
                rankings.catalogues(product.getId())
        );
    }

    private Map<UUID, String> loadShopNames(List<MarketplaceProduct> products) {
        Map<UUID, String> shopNames = new HashMap<>();
        if (products == null || products.isEmpty()) {
            return shopNames;
        }
        List<UUID> creatorIds = products.stream()
                .map(MarketplaceProduct::getCreator)
                .filter(Objects::nonNull)
                .map(User::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (creatorIds.isEmpty()) {
            return shopNames;
        }
        for (CreatorProfile profile : creatorProfileRepository.findByUser_IdIn(creatorIds)) {
            if (profile.getUser() != null && profile.getUser().getId() != null
                    && profile.getShopName() != null && !profile.getShopName().isBlank()) {
                shopNames.put(profile.getUser().getId(), profile.getShopName().trim());
            }
        }
        return shopNames;
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
