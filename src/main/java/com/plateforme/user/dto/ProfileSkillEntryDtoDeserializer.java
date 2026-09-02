package com.plateforme.user.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.UUID;

/** Hibernate JSON — accepts legacy string values and structured skill objects. */
public class ProfileSkillEntryDtoDeserializer extends JsonDeserializer<ProfileSkillEntryDto> {

    @Override
    public ProfileSkillEntryDto deserialize(JsonParser parser, DeserializationContext ctxt)
            throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            String title = parser.getValueAsString("").trim();
            if (title.isEmpty()) {
                return null;
            }
            return new ProfileSkillEntryDto(UUID.randomUUID(), 0, title, null);
        }

        JsonNode node = parser.getCodec().readTree(parser);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            String title = node.asText("").trim();
            if (title.isEmpty()) {
                return null;
            }
            return new ProfileSkillEntryDto(UUID.randomUUID(), 0, title, null);
        }
        if (!node.isObject()) {
            return null;
        }

        String title = node.hasNonNull("title") ? node.get("title").asText("").trim() : "";
        String description = node.hasNonNull("description")
                ? blankToNull(node.get("description").asText(""))
                : null;
        if (title.isEmpty()) {
            return null;
        }
        UUID id = node.hasNonNull("id")
                ? UUID.fromString(node.get("id").asText())
                : UUID.randomUUID();
        int sortOrder = node.has("sortOrder") ? node.get("sortOrder").asInt(0) : 0;
        return new ProfileSkillEntryDto(id, sortOrder, title, description);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
