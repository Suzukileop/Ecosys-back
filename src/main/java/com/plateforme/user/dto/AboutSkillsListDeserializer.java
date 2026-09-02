package com.plateforme.user.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Accepts legacy `["skill"]` JSON arrays and structured skill objects. */
public class AboutSkillsListDeserializer extends JsonDeserializer<List<ProfileSkillEntryDto>> {

    @Override
    public List<ProfileSkillEntryDto> deserialize(JsonParser parser, DeserializationContext ctxt)
            throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        List<ProfileSkillEntryDto> result = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return result;
        }
        int index = 0;
        for (JsonNode item : node) {
            if (item.isTextual()) {
                String title = item.asText("").trim();
                if (!title.isEmpty()) {
                    result.add(new ProfileSkillEntryDto(UUID.randomUUID(), index++, title, null));
                }
                continue;
            }
            if (!item.isObject()) {
                continue;
            }
            String title = item.hasNonNull("title") ? item.get("title").asText("").trim() : "";
            String description = item.hasNonNull("description")
                    ? blankToNull(item.get("description").asText(""))
                    : null;
            if (title.isEmpty()) {
                continue;
            }
            UUID id = item.hasNonNull("id")
                    ? UUID.fromString(item.get("id").asText())
                    : UUID.randomUUID();
            int sortOrder = item.has("sortOrder") ? item.get("sortOrder").asInt(index) : index;
            result.add(new ProfileSkillEntryDto(id, sortOrder, title, description));
            index++;
        }
        return result;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
