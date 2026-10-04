package com.plateforme.marketplace.service;

import com.plateforme.marketplace.dto.CatalogueBestseller;
import com.plateforme.marketplace.repository.MarketplaceProductGroupItemRepository;
import com.plateforme.marketplace.repository.MarketplaceProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bestsellers are derived from units sold, never set by hand: the top {@link #TOP_N} published products
 * of a shop, and the top {@link #TOP_N} of each of the creator's catalogues. Products without sales never rank.
 */
@Service
@RequiredArgsConstructor
public class ProductBestsellerService {

    static final int TOP_N = 3;

    private final MarketplaceProductRepository productRepository;
    private final MarketplaceProductGroupItemRepository productGroupItemRepository;

    public record Rankings(
            Map<UUID, Integer> shopRanks,
            Map<UUID, List<CatalogueBestseller>> catalogueRanks
    ) {
        public static final Rankings EMPTY = new Rankings(Map.of(), Map.of());

        public Integer shopRank(UUID productId) {
            return shopRanks.get(productId);
        }

        public List<CatalogueBestseller> catalogues(UUID productId) {
            return catalogueRanks.getOrDefault(productId, List.of());
        }
    }

    private record Seller(UUID productId, int sales) {}

    private static final Comparator<Seller> MOST_SOLD = Comparator
            .comparingInt(Seller::sales).reversed()
            .thenComparing(Seller::productId);

    public Rankings rank(Collection<UUID> creatorIds) {
        if (creatorIds == null || creatorIds.isEmpty()) {
            return Rankings.EMPTY;
        }

        Map<UUID, List<Seller>> byShop = new HashMap<>();
        for (Object[] row : productRepository.findSellingProductsByCreatorIds(creatorIds)) {
            byShop.computeIfAbsent((UUID) row[0], k -> new ArrayList<>())
                    .add(new Seller((UUID) row[1], ((Number) row[2]).intValue()));
        }
        Map<UUID, Integer> shopRanks = new HashMap<>();
        for (List<Seller> sellers : byShop.values()) {
            sellers.sort(MOST_SOLD);
            for (int i = 0; i < Math.min(TOP_N, sellers.size()); i++) {
                shopRanks.put(sellers.get(i).productId(), i + 1);
            }
        }

        Map<UUID, String> catalogueNames = new HashMap<>();
        Map<UUID, List<Seller>> byCatalogue = new HashMap<>();
        for (Object[] row : productGroupItemRepository.findSellingItemsByCreatorIds(creatorIds)) {
            UUID catalogueId = (UUID) row[0];
            catalogueNames.putIfAbsent(catalogueId, (String) row[1]);
            byCatalogue.computeIfAbsent(catalogueId, k -> new ArrayList<>())
                    .add(new Seller((UUID) row[2], ((Number) row[3]).intValue()));
        }
        Map<UUID, List<CatalogueBestseller>> catalogueRanks = new HashMap<>();
        byCatalogue.forEach((catalogueId, sellers) -> {
            sellers.sort(MOST_SOLD);
            for (int i = 0; i < Math.min(TOP_N, sellers.size()); i++) {
                catalogueRanks.computeIfAbsent(sellers.get(i).productId(), k -> new ArrayList<>())
                        .add(new CatalogueBestseller(catalogueId, catalogueNames.get(catalogueId), i + 1));
            }
        });
        catalogueRanks.values().forEach(list -> list.sort(Comparator
                .comparingInt(CatalogueBestseller::rank)
                .thenComparing(CatalogueBestseller::catalogueName, Comparator.nullsLast(String::compareToIgnoreCase))));

        return new Rankings(shopRanks, catalogueRanks);
    }
}
