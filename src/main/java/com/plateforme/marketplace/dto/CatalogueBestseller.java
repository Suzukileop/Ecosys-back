package com.plateforme.marketplace.dto;

import java.util.UUID;

/** Rank (1 = most sold) of a product among the published products of one of its creator's catalogues. */
public record CatalogueBestseller(
        UUID catalogueId,
        String catalogueName,
        int rank
) {}
