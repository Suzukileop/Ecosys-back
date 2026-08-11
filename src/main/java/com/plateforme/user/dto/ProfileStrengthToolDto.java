package com.plateforme.user.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Skill / tool mastered by a creator.
 * Legacy JSONB values were plain strings — {@link Deserializer} still accepts those.
 */
@JsonDeserialize(using = ProfileStrengthToolDto.Deserializer.class)
public record ProfileStrengthToolDto(
        String name,
        String description,
        String category,
        String level,
        List<String> useCases,
        Integer experienceYears,
        String experienceLabel,
        Boolean currentlyUsed,
        String iconUrl
) {
    public ProfileStrengthToolDto(String name, String description) {
        this(name, description, null, null, null, null, null, null, null);
    }

    public static final class Deserializer extends JsonDeserializer<ProfileStrengthToolDto> {
        @Override
        public ProfileStrengthToolDto deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            JsonNode node = parser.getCodec().readTree(parser);
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isTextual()) {
                String text = node.asText();
                return text == null || text.isBlank() ? null : new ProfileStrengthToolDto(text.trim(), null);
            }
            if (!node.isObject()) {
                return null;
            }
            String name = firstNonBlank(
                    textOrNull(node.get("name")),
                    textOrNull(node.get("value"))
            );
            if (name == null) {
                return null;
            }
            String description = textOrNull(node.get("description"));
            return new ProfileStrengthToolDto(
                    name,
                    description,
                    textOrNull(node.get("category")),
                    textOrNull(node.get("level")),
                    stringListOrNull(node.get("useCases")),
                    integerOrNull(node.get("experienceYears")),
                    textOrNull(node.get("experienceLabel")),
                    booleanOrNull(node.get("currentlyUsed")),
                    textOrNull(node.get("iconUrl"))
            );
        }

        private static String textOrNull(JsonNode node) {
            if (node == null || node.isNull() || !node.isTextual()) {
                return null;
            }
            String value = node.asText();
            return value == null || value.isBlank() ? null : value.trim();
        }

        private static List<String> stringListOrNull(JsonNode node) {
            if (node == null || node.isNull() || !node.isArray()) {
                return null;
            }
            List<String> values = new ArrayList<>();
            for (JsonNode item : node) {
                String value = textOrNull(item);
                if (value != null) {
                    values.add(value);
                }
            }
            return List.copyOf(values);
        }

        private static Integer integerOrNull(JsonNode node) {
            if (node == null || node.isNull() || !node.isIntegralNumber() || !node.canConvertToInt()) {
                return null;
            }
            return node.intValue();
        }

        private static Boolean booleanOrNull(JsonNode node) {
            if (node == null || node.isNull() || !node.isBoolean()) {
                return null;
            }
            return node.booleanValue();
        }

        private static String firstNonBlank(String a, String b) {
            if (a != null && !a.isBlank()) {
                return a.trim();
            }
            if (b != null && !b.isBlank()) {
                return b.trim();
            }
            return null;
        }
    }
}
