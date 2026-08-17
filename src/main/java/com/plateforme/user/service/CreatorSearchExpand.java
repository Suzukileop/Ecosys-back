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
 * Multi-domain keyword expansion for service-provider discovery.
 * Maps free-text / Popular chips to synonyms + common skill/tool signals
 * so search covers bio, tags, tools, and services — not only specialty labels.
 */
public final class CreatorSearchExpand {

    public static final int MAX_EXPANDED_TERMS = 14;
    public static final char TERM_SEPARATOR = '|';

    /** Domain → keywords a client might type (FR/EN + tools). */
    private static final Map<String, List<String>> DOMAIN_SIGNALS = buildDomainSignals();

    /** Tool / phrase → Popular domain (so "react" implies Developer). */
    private static final Map<String, String> SIGNAL_TO_DOMAIN = buildSignalIndex();

    private CreatorSearchExpand() {}

    /**
     * Pipe-joined terms for SQL {@code unnest(string_to_array(...))}.
     * Always includes the raw query (+ canonical specialty when known)
     * and domain synonyms / tools when the query maps to a Popular domain
     * or to a known skill signal.
     */
    public static String expandedTermsPipe(String raw) {
        return join(expandedTerms(raw));
    }

    /**
     * Signals used when a Popular chip / specialty filter is active:
     * label + synonyms + tools for that domain.
     */
    public static String specialtySignalsPipe(String specialty) {
        return join(specialtySignals(specialty));
    }

    public static List<String> expandedTerms(String raw) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String sanitized = SpecialtyTaxonomy.sanitizeLabel(raw);
        if (sanitized == null) {
            return List.of();
        }
        addTerm(terms, sanitized);
        for (String part : tokenize(sanitized)) {
            addTerm(terms, part);
        }

        String canonical = SpecialtyTaxonomy.canonicalize(sanitized);
        if (canonical != null) {
            addTerm(terms, canonical);
            addDomainBundle(terms, canonical);
        }

        String fromSignal = SIGNAL_TO_DOMAIN.get(key(sanitized));
        if (fromSignal == null) {
            for (String part : tokenize(sanitized)) {
                fromSignal = SIGNAL_TO_DOMAIN.get(key(part));
                if (fromSignal != null) {
                    break;
                }
            }
        }
        if (fromSignal != null) {
            addTerm(terms, fromSignal);
            addDomainBundle(terms, fromSignal);
        }

        // Multi-word leftovers: "spring boot" as a whole
        String compact = key(sanitized).replace(" ", "");
        if (SIGNAL_TO_DOMAIN.containsKey(key(sanitized)) || SIGNAL_TO_DOMAIN.containsKey(compact)) {
            String domain = SIGNAL_TO_DOMAIN.getOrDefault(key(sanitized), SIGNAL_TO_DOMAIN.get(compact));
            if (domain != null) {
                addTerm(terms, domain);
                addDomainBundle(terms, domain);
            }
        }

        return limit(terms);
    }

    public static List<String> specialtySignals(String specialty) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String primary = SpecialtyTaxonomy.primarySearchTerm(specialty);
        if (primary == null) {
            String sanitized = SpecialtyTaxonomy.sanitizeLabel(specialty);
            if (sanitized == null) {
                return List.of();
            }
            addTerm(terms, sanitized);
            return limit(terms);
        }
        addTerm(terms, primary);
        addDomainBundle(terms, primary);
        String alt = SpecialtyTaxonomy.alternateSearchTerm(specialty);
        if (alt != null && !alt.isBlank()) {
            addTerm(terms, alt);
        }
        return limit(terms);
    }

    private static void addDomainBundle(Set<String> terms, String domainLabel) {
        List<String> signals = DOMAIN_SIGNALS.get(domainLabel);
        if (signals == null) {
            return;
        }
        for (String signal : signals) {
            addTerm(terms, signal);
            if (terms.size() >= MAX_EXPANDED_TERMS) {
                return;
            }
        }
    }

    private static void addTerm(Set<String> terms, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.length() < 2) {
            return;
        }
        if (trimmed.indexOf(TERM_SEPARATOR) >= 0) {
            trimmed = trimmed.replace(TERM_SEPARATOR, ' ').trim();
        }
        if (trimmed.length() < 2 || trimmed.length() > SpecialtyTaxonomy.MAX_SPECIALTY_LENGTH) {
            return;
        }
        terms.add(trimmed);
    }

    private static List<String> tokenize(String value) {
        String[] parts = value.split("[\\s,/|]+");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            if (part != null && part.trim().length() >= 2) {
                out.add(part.trim());
            }
        }
        return out;
    }

    private static List<String> limit(LinkedHashSet<String> terms) {
        if (terms.size() <= MAX_EXPANDED_TERMS) {
            return List.copyOf(terms);
        }
        List<String> list = new ArrayList<>(terms);
        return List.copyOf(list.subList(0, MAX_EXPANDED_TERMS));
    }

    private static String join(List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return "";
        }
        return String.join(String.valueOf(TERM_SEPARATOR), terms);
    }

    private static String key(String value) {
        return Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static Map<String, List<String>> buildDomainSignals() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("Developer", List.of(
                "developer", "développement", "developpement", "software", "programmer", "programming",
                "coding", "fullstack", "full stack", "backend", "frontend", "web",
                "react", "spring", "spring boot", "java", "javascript", "typescript",
                "node", "python", "php", "angular", "vue", "next.js", "nextjs",
                "api", "mobile", "android", "ios", "ingénieur", "ingenieur"
        ));
        map.put("Design", List.of(
                "design", "designer", "graphic", "figma", "photoshop", "illustrator",
                "branding", "visuel", "visual", "ui", "ux", "maquette", "prototype"
        ));
        map.put("Marketing", List.of(
                "marketing", "seo", "sem", "ads", "publicité", "publicite", "growth",
                "social media", "community", "campaign", "campagne", "content marketing"
        ));
        map.put("Video editor", List.of(
                "video", "vidéo", "montage", "premiere", "after effects", "davinci",
                "capcut", "youtube", "reels", "editing", "videographer"
        ));
        map.put("UI / UX", List.of(
                "ui", "ux", "figma", "wireframe", "prototype", "user experience",
                "interface", "usability", "design system", "product design"
        ));
        map.put("Branding", List.of(
                "brand", "branding", "logo", "identité", "identite", "visual identity",
                "charte", "packaging"
        ));
        map.put("Music", List.of(
                "music", "musique", "audio", "beat", "mixing", "mastering", "producer",
                "sound", "instrumental", "song"
        ));
        map.put("Writing", List.of(
                "writing", "writer", "copywriting", "rédaction", "redaction", "content",
                "blog", "article", "script", "translation", "traduction"
        ));
        map.put("Illustration", List.of(
                "illustration", "illustrator", "drawing", "dessin", "character",
                "procreate", "comic", "artwork"
        ));
        map.put("3D", List.of(
                "3d", "blender", "cinema 4d", "maya", "modeling", "modélisation",
                "modelisation", "render", "animation 3d"
        ));
        map.put("Photography", List.of(
                "photo", "photography", "photographe", "shooting", "retouche",
                "lightroom", "portrait", "product photo"
        ));
        map.put("Data science", List.of(
                "data", "data science", "machine learning", "ml", "ai", "ia",
                "python", "pandas", "analytics", "statistique", "excel", "power bi", "sql"
        ));
        map.put("DevOps", List.of(
                "devops", "docker", "kubernetes", "k8s", "ci/cd", "aws", "azure",
                "linux", "terraform", "ansible", "cloud", "infrastructure"
        ));
        return Map.copyOf(map);
    }

    private static Map<String, String> buildSignalIndex() {
        Map<String, String> index = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : DOMAIN_SIGNALS.entrySet()) {
            String domain = entry.getKey();
            index.put(key(domain), domain);
            index.put(key(domain).replace(" ", ""), domain);
            for (String signal : entry.getValue()) {
                index.putIfAbsent(key(signal), domain);
                index.putIfAbsent(key(signal).replace(" ", ""), domain);
            }
        }
        return Map.copyOf(index);
    }
}
