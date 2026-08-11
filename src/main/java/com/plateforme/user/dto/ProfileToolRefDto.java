package com.plateforme.user.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;

/**
 * Tool / software referenced on an experience block.
 * Legacy JSONB values were plain strings — {@link Deserializer} still accepts those.
 */
@JsonDeserialize(using = ProfileToolRefDto.Deserializer.class)
public record ProfileToolRefDto(
        String name,
        String iconUrl
) {
    public ProfileToolRefDto(String name) {
        this(name, null);
    }

    public static final class Deserializer extends JsonDeserializer<ProfileToolRefDto> {
        @Override
        public ProfileToolRefDto deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            JsonNode node = parser.getCodec().readTree(parser);
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isTextual()) {
                String text = node.asText();
                return text == null || text.isBlank() ? null : new ProfileToolRefDto(text.trim(), null);
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
            return new ProfileToolRefDto(name, textOrNull(node.get("iconUrl")));
        }

        private static String textOrNull(JsonNode node) {
            if (node == null || node.isNull() || !node.isTextual()) {
                return null;
            }
            String value = node.asText();
            return value == null || value.isBlank() ? null : value.trim();
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
