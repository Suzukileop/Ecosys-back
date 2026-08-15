package com.plateforme.user.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Flat specialty tags: free-text labels (max 3, one primary) plus Popular-chip shortcuts.
 */
public final class SpecialtyTaxonomy {

    public static final int MAX_SPECIALTIES = 3;
    public static final int MAX_SPECIALTY_LENGTH = 80;
    public static final int MAX_TAGS = 8;
    public static final int MAX_TAG_LENGTH = 40;

    /** Discovery shortcuts only — not a closed input vocabulary. */
    public static final List<String> LABELS = List.of(
            "Developer",
            "Design",
            "Marketing",
            "Video editor",
            "UI / UX",
            "Branding",
            "Music",
            "Writing",
            "Illustration",
            "3D",
            "Photography",
            "Data science"
    );

    private static final Map<String, String> ALIASES = buildAliases();

    private SpecialtyTaxonomy() {}

    public static String canonicalize(String raw) {
        if (raw == null) {
            return null;
        }
        String key = key(raw);
        if (key.isEmpty()) {
            return null;
        }
        String compact = key.replaceAll("[\\s/]+", "");
        String mapped = ALIASES.get(key);
        if (mapped == null) {
            mapped = ALIASES.get(compact);
        }
        return mapped;
    }

    public static String sanitizeLabel(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_SPECIALTY_LENGTH) {
            trimmed = trimmed.substring(0, MAX_SPECIALTY_LENGTH).trim();
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static List<String> normalizeSpecialties(List<String> raw, String primaryHint) {
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        if (raw != null) {
            for (String item : raw) {
                String label = sanitizeLabel(item);
                if (label == null) {
                    continue;
                }
                unique.putIfAbsent(label.toLowerCase(Locale.ROOT), label);
                if (unique.size() >= MAX_SPECIALTIES) {
                    break;
                }
            }
        }
        String primary = sanitizeLabel(primaryHint);
        if (primary != null) {
            String primaryKey = primary.toLowerCase(Locale.ROOT);
            if (unique.containsKey(primaryKey)) {
                unique.put(primaryKey, primary);
            } else if (unique.size() < MAX_SPECIALTIES) {
                unique.put(primaryKey, primary);
            }
        }
        List<String> list = new ArrayList<>(unique.values());
        if (primary != null) {
            String storedPrimary = unique.get(primary.toLowerCase(Locale.ROOT));
            if (storedPrimary != null) {
                list.remove(storedPrimary);
                list.add(0, storedPrimary);
            }
        }
        if (list.size() > MAX_SPECIALTIES) {
            return List.copyOf(list.subList(0, MAX_SPECIALTIES));
        }
        return List.copyOf(list);
    }

    public static String primaryOf(List<String> specialties) {
        if (specialties == null || specialties.isEmpty()) {
            return null;
        }
        return specialties.get(0);
    }

    public static String matchAllowed(String category, List<String> allowed) {
        if (category == null || allowed == null) {
            return null;
        }
        String wanted = category.trim();
        if (wanted.isEmpty()) {
            return null;
        }
        for (String item : allowed) {
            if (item != null && item.equalsIgnoreCase(wanted)) {
                return item;
            }
        }
        return null;
    }

    public static boolean matchesFilter(String specialty, String filter) {
        String needle = key(filter == null ? "" : filter);
        if (needle.isEmpty()) {
            return false;
        }
        String haystack = key(specialty == null ? "" : specialty);
        if (haystack.contains(needle)) {
            return true;
        }
        String compactNeedle = needle.replaceAll("[\\s/]+", "");
        String compactHaystack = haystack.replaceAll("[\\s/]+", "");
        return !compactNeedle.isEmpty() && compactHaystack.contains(compactNeedle);
    }

    public static List<String> normalizeTags(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> tags = new ArrayList<>();
        for (String item : raw) {
            if (item == null) {
                continue;
            }
            String trimmed = item.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > MAX_TAG_LENGTH) {
                trimmed = trimmed.substring(0, MAX_TAG_LENGTH);
            }
            String tagKey = trimmed.toLowerCase(Locale.ROOT);
            if (!seen.add(tagKey)) {
                continue;
            }
            tags.add(trimmed);
            if (tags.size() >= MAX_TAGS) {
                break;
            }
        }
        return List.copyOf(tags);
    }

    private static String key(String value) {
        String normalized = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }

    private static Map<String, String> buildAliases() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String label : LABELS) {
            map.put(key(label), label);
            map.put(key(label).replaceAll("[\\s/]+", ""), label);
        }
        put(map, "Developer", "development", "dev", "software developer", "software engineer", "programmer");
        put(map, "Design", "designer", "graphic design", "graphic designer");
        put(map, "Marketing", "marketer");
        put(map, "Video editor", "video editing", "videographer", "video");
        put(map, "UI / UX", "ui/ux", "uiux", "ui ux", "ux", "ui", "user experience");
        put(map, "Branding", "brand");
        put(map, "Music", "musician");
        put(map, "Writing", "writer", "copywriting");
        put(map, "Illustration", "illustrator");
        put(map, "Photography", "photographer", "photo");
        put(map, "Data science", "data scientist", "datascience", "data scien");
        return Map.copyOf(map);
    }

    private static void put(Map<String, String> map, String label, String... aliases) {
        for (String alias : aliases) {
            map.put(key(alias), label);
            map.put(key(alias).replaceAll("[\\s/]+", ""), label);
        }
    }
}
