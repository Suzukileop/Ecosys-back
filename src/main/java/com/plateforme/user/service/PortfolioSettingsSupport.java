package com.plateforme.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.entity.CreatorProfile;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * Opaque JSON portfolio presentation document stored on {@code creator_profiles.portfolio_settings}.
 * Frontend owns the schema; this class only validates size/JSON and stamps {@code updatedAt}.
 */
public final class PortfolioSettingsSupport {

    private static final int MAX_JSON_BYTES = 512_000;

    private PortfolioSettingsSupport() {
    }

    public static Map<String, Object> read(CreatorProfile profile) {
        Map<String, Object> settings = profile.getPortfolioSettings();
        if (settings == null || settings.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(settings);
    }

    public static Map<String, Object> normalize(Map<String, Object> raw, ObjectMapper objectMapper) {
        if (raw == null) {
            return Map.of();
        }
        try {
            String json = objectMapper.writeValueAsString(raw);
            if (json.length() > MAX_JSON_BYTES) {
                throw new BusinessException(
                        "PORTFOLIO_SETTINGS_TOO_LARGE",
                        "Portfolio settings payload is too large (max 512 KB). Remove unused custom themes or large embedded images.");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
            Map<String, Object> normalized = new HashMap<>(parsed);
            stampUpdatedAt(normalized);
            return normalized;
        } catch (JsonProcessingException ex) {
            throw new BusinessException("PORTFOLIO_SETTINGS_INVALID", "Portfolio settings JSON is invalid.");
        }
    }

    /**
     * Keep a valid client {@code updatedAt} when present; otherwise stamp server time.
     * Unknown keys are preserved — do not introduce a strict DTO that drops forward-compatible fields.
     */
    static void stampUpdatedAt(Map<String, Object> settings) {
        Object existing = settings.get("updatedAt");
        if (existing instanceof String text && isValidIsoInstant(text)) {
            settings.put("updatedAt", text.trim());
            return;
        }
        settings.put("updatedAt", Instant.now().toString());
    }

    private static boolean isValidIsoInstant(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            Instant.parse(value.trim());
            return true;
        } catch (DateTimeParseException ex) {
            return false;
        }
    }
}
