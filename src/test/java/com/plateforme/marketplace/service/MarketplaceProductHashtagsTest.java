package com.plateforme.marketplace.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketplaceProductHashtagsTest {

    @Test
    void stripsHashAndInvalidCharactersAndDeduplicates() {
        List<String> result = MarketplaceProductService.normalizeHashtags(
                Arrays.asList("#MotionDesign", "motiondesign", "  ##after effects ", "", null, "UI-kit", "café!"));

        assertThat(result).containsExactly("MotionDesign", "aftereffects", "UI-kit", "café");
    }

    @Test
    void capsCountAndLength() {
        List<String> raw = new ArrayList<>();
        for (int i = 0; i < 30; i++) raw.add("tag" + i);
        raw.set(0, "a".repeat(80));

        List<String> result = MarketplaceProductService.normalizeHashtags(raw);

        assertThat(result).hasSize(MarketplaceProductService.MAX_HASHTAGS);
        assertThat(result.get(0)).hasSize(MarketplaceProductService.MAX_HASHTAG_LENGTH);
    }

    @Test
    void nullOrEmptyGivesEmptyList() {
        assertThat(MarketplaceProductService.normalizeHashtags(null)).isEmpty();
        assertThat(MarketplaceProductService.normalizeHashtags(List.of())).isEmpty();
    }
}
