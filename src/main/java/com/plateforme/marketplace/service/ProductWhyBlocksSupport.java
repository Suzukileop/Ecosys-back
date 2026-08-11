package com.plateforme.marketplace.service;

import com.plateforme.marketplace.dto.ProductWhyBlock;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.shared.util.OwnedMediaUrlValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ProductWhyBlocksSupport {

    static final int MAX_BLOCKS = 10;
    static final int MAX_OPINIONS_PER_BLOCK = 20;
    static final int MAX_OPINION_LENGTH = 500;

    private ProductWhyBlocksSupport() {}

    public static List<ProductWhyBlock> normalizeBlocks(List<ProductWhyBlock> raw, UUID userId) {
        if (raw == null) {
            return List.of();
        }
        if (raw.size() > MAX_BLOCKS) {
            throw new BusinessException("TOO_MANY_WHY_BLOCKS",
                    "A maximum of " + MAX_BLOCKS + " why-product blocks is allowed.");
        }
        List<ProductWhyBlock> normalized = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            ProductWhyBlock block = raw.get(i);
            if (block == null) {
                continue;
            }
            String mediaUrl = blankToNull(block.mediaUrl());
            List<String> opinions = normalizeOpinions(block.opinions());
            if (mediaUrl == null && opinions.isEmpty()) {
                continue;
            }
            if (mediaUrl == null) {
                throw new BusinessException("WHY_BLOCK_MEDIA_URL_REQUIRED",
                        "Each why-product block must include a media URL.");
            }
            String mediaType = blankToNull(block.mediaType());
            if (mediaType == null) {
                throw new BusinessException("WHY_BLOCK_MEDIA_TYPE_REQUIRED",
                        "Each why-product block must include a media type.");
            }
            if (!"IMAGE".equals(mediaType) && !"VIDEO".equals(mediaType)) {
                throw new BusinessException("WHY_BLOCK_MEDIA_TYPE_INVALID",
                        "Media type must be IMAGE or VIDEO.");
            }
            if (opinions.isEmpty()) {
                throw new BusinessException("WHY_BLOCK_OPINIONS_REQUIRED",
                        "Each why-product block must include at least one opinion.");
            }
            OwnedMediaUrlValidator.validate(mediaUrl, userId);
            UUID id = block.id() != null ? block.id() : UUID.randomUUID();
            int sortOrder = block.sortOrder() >= 0 ? block.sortOrder() : i;
            normalized.add(new ProductWhyBlock(id, sortOrder, mediaUrl, mediaType, opinions));
        }
        normalized.sort((a, b) -> Integer.compare(a.sortOrder(), b.sortOrder()));
        return List.copyOf(normalized);
    }

    private static List<String> normalizeOpinions(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String item : raw) {
            if (item == null) {
                continue;
            }
            String trimmed = item.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > MAX_OPINION_LENGTH) {
                throw new BusinessException("WHY_BLOCK_OPINION_TOO_LONG",
                        "Each opinion must be at most " + MAX_OPINION_LENGTH + " characters.");
            }
            normalized.add(trimmed);
            if (normalized.size() > MAX_OPINIONS_PER_BLOCK) {
                throw new BusinessException("TOO_MANY_WHY_BLOCK_OPINIONS",
                        "A maximum of " + MAX_OPINIONS_PER_BLOCK + " opinions is allowed per block.");
            }
        }
        return List.copyOf(normalized);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
