package com.plateforme.marketplace.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plateforme.marketplace.dto.SocialLink;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class SocialLinksJsonParser {

    private SocialLinksJsonParser() {}

    public static List<SocialLink> parse(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.isArray()) {
                return parseArray(root);
            }
            if (root.isObject()) {
                return parseObject(root);
            }
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<SocialLink> parseArray(JsonNode root) {
        List<SocialLink> out = new ArrayList<>();
        for (JsonNode node : root) {
            String platform = "";
            if (node.hasNonNull("platform")) {
                platform = node.get("platform").asText("");
            } else if (node.hasNonNull("name")) {
                platform = node.get("name").asText("");
            }
            String url = node.path("url").asText("");
            if (!url.isBlank()) {
                out.add(new SocialLink(platform, url));
            }
        }
        return out;
    }

    private static List<SocialLink> parseObject(JsonNode root) {
        List<SocialLink> out = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String url = entry.getValue().isTextual() ? entry.getValue().asText("") : entry.getValue().path("url").asText("");
            if (!url.isBlank()) {
                out.add(new SocialLink(entry.getKey(), url));
            }
        }
        return out;
    }
}
