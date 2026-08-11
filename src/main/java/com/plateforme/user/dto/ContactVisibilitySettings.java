package com.plateforme.user.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public record ContactVisibilitySettings(
        ContactVisibilityLevel website,
        ContactVisibilityLevel email,
        ContactVisibilityLevel phone,
        ContactVisibilityLevel availability,
        ContactVisibilityLevel address,
        ContactVisibilityLevel social,
        ContactVisibilityLevel languages,
        ContactVisibilityLevel cta,
        ContactVisibilityLevel whyMe,
        ContactVisibilityLevel experience,
        ContactVisibilityLevel yearsOfExperience,
        ContactVisibilityLevel strengthsTools,
        ContactVisibilityLevel services,
        ContactVisibilityLevel faq,
        ContactVisibilityLevel links,
        ContactVisibilityLevel gender,
        ContactVisibilityLevel spokenLanguages,
        ContactVisibilityLevel responseTime
) {
    public static ContactVisibilitySettings defaults() {
        return new ContactVisibilitySettings(
                ContactVisibilityLevel.PUBLIC,
                ContactVisibilityLevel.MEMBERS,
                ContactVisibilityLevel.MEMBERS,
                ContactVisibilityLevel.PUBLIC,
                ContactVisibilityLevel.HIDDEN,
                ContactVisibilityLevel.PUBLIC,
                ContactVisibilityLevel.PUBLIC,
                ContactVisibilityLevel.PUBLIC,
                ContactVisibilityLevel.PUBLIC,
                ContactVisibilityLevel.PUBLIC,
                ContactVisibilityLevel.PUBLIC,
                ContactVisibilityLevel.PUBLIC,
                ContactVisibilityLevel.PUBLIC,
                ContactVisibilityLevel.PUBLIC,
                ContactVisibilityLevel.PUBLIC,
                ContactVisibilityLevel.PUBLIC,
                ContactVisibilityLevel.PUBLIC,
                ContactVisibilityLevel.PUBLIC
        );
    }

    /** Default JSON for new creator profiles (no ObjectMapper required). */
    public static String defaultJson() {
        return "{"
                + "\"website\":\"PUBLIC\","
                + "\"email\":\"MEMBERS\","
                + "\"phone\":\"MEMBERS\","
                + "\"availability\":\"PUBLIC\","
                + "\"address\":\"HIDDEN\","
                + "\"social\":\"PUBLIC\","
                + "\"languages\":\"PUBLIC\","
                + "\"cta\":\"PUBLIC\","
                + "\"whyMe\":\"PUBLIC\","
                + "\"experience\":\"PUBLIC\","
                + "\"yearsOfExperience\":\"PUBLIC\","
                + "\"strengthsTools\":\"PUBLIC\","
                + "\"services\":\"PUBLIC\","
                + "\"faq\":\"PUBLIC\","
                + "\"links\":\"PUBLIC\","
                + "\"gender\":\"PUBLIC\","
                + "\"spokenLanguages\":\"PUBLIC\","
                + "\"responseTime\":\"PUBLIC\""
                + "}";
    }

    public static ContactVisibilitySettings fromJson(ObjectMapper mapper, String json) {
        ContactVisibilitySettings defaults = defaults();
        if (json == null || json.isBlank()) {
            return defaults;
        }
        try {
            JsonNode root = mapper.readTree(json);
            return new ContactVisibilitySettings(
                    level(root, "website", defaults.website()),
                    level(root, "email", defaults.email()),
                    level(root, "phone", defaults.phone()),
                    level(root, "availability", defaults.availability()),
                    level(root, "address", defaults.address()),
                    level(root, "social", defaults.social()),
                    level(root, "languages", defaults.languages()),
                    level(root, "cta", defaults.cta()),
                    level(root, "whyMe", defaults.whyMe()),
                    level(root, "experience", defaults.experience()),
                    level(root, "yearsOfExperience", defaults.yearsOfExperience()),
                    level(root, "strengthsTools", defaults.strengthsTools()),
                    level(root, "services", defaults.services()),
                    level(root, "faq", defaults.faq()),
                    level(root, "links", defaults.links()),
                    levelWithLegacy(root, "gender", "pronouns", defaults.gender()),
                    level(root, "spokenLanguages", defaults.spokenLanguages()),
                    level(root, "responseTime", defaults.responseTime())
            );
        } catch (Exception e) {
            return defaults;
        }
    }

    private static ContactVisibilityLevel level(JsonNode root, String key, ContactVisibilityLevel fallback) {
        if (!root.hasNonNull(key)) {
            return fallback;
        }
        return ContactVisibilityLevel.fromString(root.get(key).asText());
    }

    private static ContactVisibilityLevel levelWithLegacy(
            JsonNode root, String key, String legacyKey, ContactVisibilityLevel fallback) {
        if (root.hasNonNull(key)) {
            return ContactVisibilityLevel.fromString(root.get(key).asText());
        }
        if (root.hasNonNull(legacyKey)) {
            return ContactVisibilityLevel.fromString(root.get(legacyKey).asText());
        }
        return fallback;
    }

    public String toJson(ObjectMapper mapper) {
        try {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("website", website.name());
            payload.put("email", email.name());
            payload.put("phone", phone.name());
            payload.put("availability", availability.name());
            payload.put("address", address.name());
            payload.put("social", social.name());
            payload.put("languages", languages.name());
            payload.put("cta", cta.name());
            payload.put("whyMe", whyMe.name());
            payload.put("experience", experience.name());
            payload.put("yearsOfExperience", yearsOfExperience.name());
            payload.put("strengthsTools", strengthsTools.name());
            payload.put("services", services.name());
            payload.put("faq", faq.name());
            payload.put("links", links.name());
            payload.put("gender", gender.name());
            payload.put("spokenLanguages", spokenLanguages.name());
            payload.put("responseTime", responseTime.name());
            return mapper.writeValueAsString(payload);
        } catch (Exception e) {
            return null;
        }
    }
}
