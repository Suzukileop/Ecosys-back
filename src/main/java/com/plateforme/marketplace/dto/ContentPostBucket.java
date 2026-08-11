package com.plateforme.marketplace.dto;

public enum ContentPostBucket {
    ACTIVE,
    PINNED,
    ARCHIVED,
    TRASH;

    public static ContentPostBucket fromParam(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        return switch (value.trim().toLowerCase()) {
            case "pinned" -> PINNED;
            case "archived" -> ARCHIVED;
            case "trash" -> TRASH;
            default -> ACTIVE;
        };
    }
}
