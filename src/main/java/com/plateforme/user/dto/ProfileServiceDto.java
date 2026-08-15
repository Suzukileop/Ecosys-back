package com.plateforme.user.dto;

import java.util.List;
import java.util.UUID;

public record ProfileServiceDto(
        UUID id,
        int sortOrder,
        String title,
        String description,
        Integer basePriceCents,
        String deadline,
        List<String> tasks,
        /** Must match one of the creator's profile specialties. */
        String specialty,
        /** FIXED | FROM | QUOTE */
        String pricingType,
        String coverImageUrl,
        /** ACTIVE | PAUSED | ARCHIVED */
        String status,
        List<String> tags,
        /** ISO-ish currency code; defaults to MGA when absent. */
        String currency,
        /** Structured delivery quantity (paired with deliveryUnit). */
        Integer deliveryValue,
        /** DAYS | WEEKS */
        String deliveryUnit
) {
    /** Backward-compatible constructor for legacy call sites / tests. */
    public ProfileServiceDto(
            UUID id,
            int sortOrder,
            String title,
            String description,
            Integer basePriceCents,
            String deadline,
            List<String> tasks) {
        this(id, sortOrder, title, description, basePriceCents, deadline, tasks,
                null, null, null, null, List.of(), "MGA", null, null);
    }

    /** Backward-compatible constructor used before currency/delivery fields. */
    public ProfileServiceDto(
            UUID id,
            int sortOrder,
            String title,
            String description,
            Integer basePriceCents,
            String deadline,
            List<String> tasks,
            String specialty,
            String pricingType,
            String coverImageUrl,
            String status,
            List<String> tags) {
        this(id, sortOrder, title, description, basePriceCents, deadline, tasks,
                specialty, pricingType, coverImageUrl, status, tags, "MGA", null, null);
    }
}
