package com.plateforme.ecosystem.storage;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StorageObjectKeysTest {

    @Test
    void sanitizeFileName_replacesSpacesAndUnsafeChars() {
        assertThat(StorageObjectKeys.sanitizeFileName("Screenshot 2026-06-12 084417.png"))
                .isEqualTo("Screenshot_2026-06-12_084417.png");
    }

    @Test
    void uniqueObjectKey_producesSafePath() {
        UUID id = UUID.fromString("de7ce938-0370-4027-a1d2-bad3dc57bd5c");
        String key = StorageObjectKeys.uniqueObjectKey("niche-deliveries", id, "My Photo (1).jpg");
        assertThat(key).startsWith("niche-deliveries/de7ce938-0370-4027-a1d2-bad3dc57bd5c/");
        assertThat(key).doesNotContain(" ");
        assertThat(key).endsWith(".jpg");
    }
}
