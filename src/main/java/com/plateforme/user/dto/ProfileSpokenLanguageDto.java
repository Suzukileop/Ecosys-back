package com.plateforme.user.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;

/**
 * Spoken / working language with optional proficiency level.
 * Legacy JSONB values were plain strings — {@link Deserializer} still accepts those.
 */
@JsonDeserialize(using = ProfileSpokenLanguageDto.Deserializer.class)
public record ProfileSpokenLanguageDto(
        String name,
        String level
) {
    public ProfileSpokenLanguageDto(String name) {
        this(name, null);
    }

    public static final class Deserializer extends JsonDeserializer<ProfileSpokenLanguageDto> {
        @Override
        public ProfileSpokenLanguageDto deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            JsonNode node = parser.getCodec().readTree(parser);
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isTextual()) {
                String text = node.asText();
                return text == null || text.isBlank() ? null : new ProfileSpokenLanguageDto(text.trim());
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
            return new ProfileSpokenLanguageDto(name, textOrNull(node.get("level")));
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
