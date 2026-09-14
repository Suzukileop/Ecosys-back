package com.plateforme.user.service;

import com.plateforme.user.entity.CreatorProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioSettingsSupportTest {

    @Test
    @DisplayName("read copies JSON even when a nested value is null")
    void read_allowsNullValues() {
        CreatorProfile profile = new CreatorProfile();
        Map<String, Object> stored = new HashMap<>();
        stored.put("themeId", "noir");
        stored.put("global", null);
        profile.setPortfolioSettings(stored);

        Map<String, Object> read = PortfolioSettingsSupport.read(profile);

        assertThat(read).containsEntry("themeId", "noir");
        assertThat(read).containsEntry("global", null);
    }

    @Test
    @DisplayName("stale write is ignored when incoming updatedAt is older")
    void isStaleWrite_olderIncoming() {
        Map<String, Object> existing = Map.of("updatedAt", "2026-09-12T20:00:00Z", "themeId", "noir");
        Map<String, Object> incoming = Map.of("updatedAt", "2026-09-12T19:00:00Z", "themeId", "editorial");

        assertThat(PortfolioSettingsSupport.isStaleWrite(existing, incoming)).isTrue();
    }

    @Test
    @DisplayName("newer incoming write is accepted")
    void isStaleWrite_newerIncoming() {
        Map<String, Object> existing = Map.of("updatedAt", "2026-09-12T19:00:00Z");
        Map<String, Object> incoming = Map.of("updatedAt", "2026-09-12T20:00:00Z");

        assertThat(PortfolioSettingsSupport.isStaleWrite(existing, incoming)).isFalse();
    }

    @Test
    @DisplayName("missing timestamps are not treated as stale")
    void isStaleWrite_missingTimestamps() {
        assertThat(PortfolioSettingsSupport.isStaleWrite(Map.of(), Map.of("themeId", "noir"))).isFalse();
        assertThat(PortfolioSettingsSupport.isStaleWrite(Map.of("updatedAt", "2026-09-12T20:00:00Z"), Map.of()))
                .isFalse();
    }
}
