package com.plateforme.user.dto;

public enum ContactVisibilityLevel {
    PUBLIC,
    MEMBERS,
    HIDDEN;

    public static ContactVisibilityLevel fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return HIDDEN;
        }
        try {
            return ContactVisibilityLevel.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return HIDDEN;
        }
    }

    public boolean visibleTo(boolean authenticated) {
        return switch (this) {
            case PUBLIC -> true;
            case MEMBERS -> authenticated;
            case HIDDEN -> false;
        };
    }
}
