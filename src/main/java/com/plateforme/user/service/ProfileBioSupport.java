package com.plateforme.user.service;

/**
 * Bio helpers: prevent accidental duplicated paragraphs from being persisted.
 */
public final class ProfileBioSupport {

    private ProfileBioSupport() {}

    /** True when the trimmed bio is an exact back-to-back duplication of the same half. */
    public static boolean isRepeatedContent(String bio) {
        if (bio == null) {
            return false;
        }
        String trimmed = bio.trim();
        if (trimmed.length() < 40 || trimmed.length() % 2 != 0) {
            return false;
        }
        int half = trimmed.length() / 2;
        return trimmed.substring(0, half).equals(trimmed.substring(half));
    }

    /** Collapse an exact half-duplicated bio; otherwise return trimmed text (empty → null). */
    public static String normalize(String bio) {
        if (bio == null) {
            return null;
        }
        String trimmed = bio.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (isRepeatedContent(trimmed)) {
            return trimmed.substring(0, trimmed.length() / 2).trim();
        }
        return trimmed;
    }
}
