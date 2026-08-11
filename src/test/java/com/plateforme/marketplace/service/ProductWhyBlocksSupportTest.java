package com.plateforme.marketplace.service;

import com.plateforme.marketplace.dto.ProductWhyBlock;
import com.plateforme.shared.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductWhyBlocksSupportTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String OWNED_MEDIA_URL =
            "https://cdn.example.com/marketplace/public/" + USER_ID + "/image.jpg";

    @Test
    @DisplayName("normalizeBlocks assigns ids, sorts, and keeps opinions")
    void normalizeBlocks_sortsAndAssignsIds() {
        UUID id = UUID.randomUUID();
        List<ProductWhyBlock> result = ProductWhyBlocksSupport.normalizeBlocks(
                List.of(
                        new ProductWhyBlock(id, 1, OWNED_MEDIA_URL, "IMAGE", List.of("Second opinion")),
                        new ProductWhyBlock(null, 0, OWNED_MEDIA_URL, "VIDEO", List.of("First opinion"))
                ),
                USER_ID
        );

        assertEquals(2, result.size());
        assertEquals("First opinion", result.get(0).opinions().get(0));
        assertEquals("Second opinion", result.get(1).opinions().get(0));
        assertEquals(id, result.get(1).id());
    }

    @Test
    @DisplayName("normalizeBlocks filters empty blocks")
    void normalizeBlocks_filtersEmptyBlocks() {
        List<ProductWhyBlock> result = ProductWhyBlocksSupport.normalizeBlocks(
                List.of(
                        new ProductWhyBlock(null, 0, null, null, List.of()),
                        new ProductWhyBlock(null, 1, OWNED_MEDIA_URL, "IMAGE", List.of("Valid"))
                ),
                USER_ID
        );

        assertEquals(1, result.size());
        assertEquals("Valid", result.get(0).opinions().get(0));
    }

    @Test
    @DisplayName("normalizeBlocks requires media URL for non-empty blocks")
    void normalizeBlocks_requiresMediaUrl() {
        assertThrows(BusinessException.class, () -> ProductWhyBlocksSupport.normalizeBlocks(
                List.of(new ProductWhyBlock(UUID.randomUUID(), 0, null, null, List.of("Opinion"))),
                USER_ID
        ));
    }

    @Test
    @DisplayName("normalizeBlocks requires at least one opinion")
    void normalizeBlocks_requiresOpinions() {
        assertThrows(BusinessException.class, () -> ProductWhyBlocksSupport.normalizeBlocks(
                List.of(new ProductWhyBlock(UUID.randomUUID(), 0, OWNED_MEDIA_URL, "IMAGE", List.of())),
                USER_ID
        ));
    }

    @Test
    @DisplayName("normalizeBlocks rejects foreign media URLs")
    void normalizeBlocks_rejectsForeignMediaUrl() {
        assertThrows(BusinessException.class, () -> ProductWhyBlocksSupport.normalizeBlocks(
                List.of(new ProductWhyBlock(
                        UUID.randomUUID(), 0, "https://example.com/x.jpg", "IMAGE", List.of("Opinion"))),
                USER_ID
        ));
    }

    @Test
    @DisplayName("normalizeBlocks trims opinions and enforces limits")
    void normalizeBlocks_normalizesOpinions() {
        List<ProductWhyBlock> result = ProductWhyBlocksSupport.normalizeBlocks(
                List.of(new ProductWhyBlock(
                        UUID.randomUUID(),
                        0,
                        OWNED_MEDIA_URL,
                        "IMAGE",
                        List.of(" First ", "", "Second"))),
                USER_ID
        );

        assertEquals(List.of("First", "Second"), result.get(0).opinions());
    }
}
