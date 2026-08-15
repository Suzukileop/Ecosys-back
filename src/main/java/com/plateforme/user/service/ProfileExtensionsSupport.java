package com.plateforme.user.service;

import com.plateforme.shared.exception.BusinessException;
import com.plateforme.shared.util.OwnedMediaUrlValidator;
import com.plateforme.user.dto.FaqItemDto;
import com.plateforme.user.dto.ProfileContactEntryDto;
import com.plateforme.user.dto.ProfileGalleryItemDto;
import com.plateforme.user.dto.ProfileLinkDto;
import com.plateforme.user.dto.ProfileServiceDto;
import com.plateforme.user.dto.ProfileTeamMemberDto;
import com.plateforme.user.dto.ProfileTeamSocialLinkDto;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class ProfileExtensionsSupport {

    static final int MAX_FAQ = 5;
    static final int MAX_SERVICES = 8;
    static final int MAX_LINKS = 10;
    static final int MAX_SPOKEN_LANGUAGES = 10;
    static final int MAX_FAQ_QUESTION = 200;
    static final int MAX_FAQ_ANSWER = 1000;
    static final int MAX_SERVICE_TITLE = 100;
    static final int MAX_SERVICE_DESC = 500;
    static final int MAX_SERVICE_DEADLINE = 100;
    static final int MAX_SERVICE_TASKS = 12;
    static final int MAX_SERVICE_TASK = 120;
    static final int MAX_SERVICE_TAGS = 8;
    static final int MAX_SERVICE_TAG_LENGTH = 40;
    static final int MAX_SERVICE_COVER_URL = 500;
    static final int MAX_LANGUAGE_LENGTH = 50;
    static final int MAX_LINK_LABEL = 100;
    static final int MAX_LINK_URL = 500;
    static final int MAX_TEAM_MEMBERS = 12;
    static final int MAX_TEAM_SOCIAL_LINKS = 6;
    static final int MAX_TEAM_NAME = 100;
    static final int MAX_TEAM_RESPONSIBILITY = 120;
    static final int MAX_TEAM_SOCIAL_LABEL = 80;
    static final int MAX_GALLERY_ITEMS = 24;
    static final int MAX_GALLERY_TITLE = 120;
    static final int MIN_SAMPLES_FOR_LABEL = 1;
    static final int MAX_CONTACT_ENTRIES = 8;
    static final int MAX_CONTACT_ADDRESS = 300;
    static final int MAX_CONTACT_PHONE = 50;
    static final int MAX_CONTACT_EMAIL = 255;

    private static final Set<String> ALLOWED_LINK_TYPES = Set.of("WEBSITE", "SOCIAL", "CTA", "CUSTOM");
    private static final Set<String> ALLOWED_TEAM_SOCIAL_PLATFORMS = Set.of(
            "FACEBOOK", "X", "TWITTER", "LINKEDIN", "INSTAGRAM", "YOUTUBE",
            "GITHUB", "WEBSITE", "EMAIL", "OTHER"
    );
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            "mp4", "webm", "mov", "m4v", "avi", "mkv", "ogv"
    );

    private static final List<String> SPOKEN_LANGUAGE_PRESETS = List.of(
            "Français", "English", "Español", "Deutsch", "Italiano", "Português",
            "العربية", "中文", "日本語", "한국어", "Русский", "Nederlands"
    );

    private ProfileExtensionsSupport() {}

    static String languageMatchKey(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }

    static String canonicalSpokenLanguage(String trimmed) {
        String key = languageMatchKey(trimmed);
        for (String preset : SPOKEN_LANGUAGE_PRESETS) {
            if (languageMatchKey(preset).equals(key)) {
                return preset;
            }
        }
        return trimmed;
    }

    public static final String DEFAULT_APP_ROLE = "GENERAL_MEMBER";

    private static final Set<String> APP_ROLES = Set.of(
            "GENERAL_MEMBER",
            "SERVICE_PROVIDER",
            "FREELANCER_STUDENT",
            "SELLER",
            "RH_RECRUITER"
    );

    private static final Set<String> LEGACY_SERVICE_PROVIDER_ROLES = Set.of(
            "JOB_SEEKER"
    );

    /**
     * Platform experience role (Information → My Role). Always returns a valid enum value.
     * Job Seeker is merged into SERVICE_PROVIDER.
     */
    public static String normalizeAppRole(String raw) {
        if (raw == null) {
            return DEFAULT_APP_ROLE;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_APP_ROLE;
        }
        String token = trimmed.toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        if (LEGACY_SERVICE_PROVIDER_ROLES.contains(token)) {
            return "SERVICE_PROVIDER";
        }
        if ("STUDENT".equals(token) || "FREELANCER".equals(token)) {
            return "FREELANCER_STUDENT";
        }
        if (APP_ROLES.contains(token)) {
            return token;
        }
        String key = Normalizer.normalize(trimmed.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        if (key.contains("seller") || key.contains("catalog")) {
            return "SELLER";
        }
        if (key.contains("service") || key.contains("provider")
                || key.contains("job") || key.contains("seeker")) {
            return "SERVICE_PROVIDER";
        }
        if (key.contains("freelancer") || key.contains("student")) {
            return "FREELANCER_STUDENT";
        }
        if (key.contains("recruiter") || key.contains("client")
                || key.equals("rh") || key.startsWith("rh ")) {
            return "RH_RECRUITER";
        }
        if (key.contains("general") || key.contains("member")) {
            return "GENERAL_MEMBER";
        }
        return DEFAULT_APP_ROLE;
    }

    public static String normalizeGender(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String key = Normalizer.normalize(trimmed.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        if (key.equals("homme") || key.equals("man") || key.equals("male") || key.equals("m")) {
            return "Male";
        }
        if (key.equals("femme") || key.equals("woman") || key.equals("female") || key.equals("f")) {
            return "Female";
        }
        return null;
    }

    /** ISO 3166-1 alpha-2, uppercase. Empty clears. Invalid values are ignored as null. */
    public static String normalizeNationality(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().toUpperCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!trimmed.matches("[A-Z]{2}")) {
            return null;
        }
        return trimmed;
    }

    public static List<String> normalizeSpokenLanguages(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Set<String> uniqueKeys = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String item : raw) {
            if (item == null) {
                continue;
            }
            String trimmed = item.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > MAX_LANGUAGE_LENGTH) {
                throw new BusinessException("LANGUAGE_TOO_LONG",
                        "Each language must be at most " + MAX_LANGUAGE_LENGTH + " characters.");
            }
            String canonical = canonicalSpokenLanguage(trimmed);
            String key = languageMatchKey(canonical);
            if (!uniqueKeys.add(key)) {
                continue;
            }
            result.add(canonical);
            if (result.size() > MAX_SPOKEN_LANGUAGES) {
                throw new BusinessException("TOO_MANY_LANGUAGES",
                        "A maximum of " + MAX_SPOKEN_LANGUAGES + " languages is allowed.");
            }
        }
        return List.copyOf(result);
    }

    public static List<FaqItemDto> normalizeFaqItems(List<FaqItemDto> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        if (raw.size() > MAX_FAQ) {
            throw new BusinessException("TOO_MANY_FAQ",
                    "A maximum of " + MAX_FAQ + " FAQ items is allowed.");
        }
        List<FaqItemDto> normalized = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            FaqItemDto item = raw.get(i);
            if (item == null) {
                continue;
            }
            String question = requireText(item.question(), "FAQ_QUESTION_REQUIRED", "Each FAQ item needs a question.");
            String answer = requireText(item.answer(), "FAQ_ANSWER_REQUIRED", "Each FAQ item needs an answer.");
            if (question.length() > MAX_FAQ_QUESTION) {
                throw new BusinessException("FAQ_QUESTION_TOO_LONG",
                        "FAQ questions must be at most " + MAX_FAQ_QUESTION + " characters.");
            }
            if (answer.length() > MAX_FAQ_ANSWER) {
                throw new BusinessException("FAQ_ANSWER_TOO_LONG",
                        "FAQ answers must be at most " + MAX_FAQ_ANSWER + " characters.");
            }
            UUID id = item.id() != null ? item.id() : UUID.randomUUID();
            int sortOrder = item.sortOrder() >= 0 ? item.sortOrder() : i;
            normalized.add(new FaqItemDto(id, sortOrder, question, answer));
        }
        normalized.sort((a, b) -> Integer.compare(a.sortOrder(), b.sortOrder()));
        return List.copyOf(normalized);
    }

    public static List<ProfileTeamMemberDto> normalizeTeamMembers(
            List<ProfileTeamMemberDto> raw,
            UUID userId
    ) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        if (raw.size() > MAX_TEAM_MEMBERS) {
            throw new BusinessException("TOO_MANY_TEAM_MEMBERS",
                    "A maximum of " + MAX_TEAM_MEMBERS + " team members is allowed.");
        }
        List<ProfileTeamMemberDto> normalized = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            ProfileTeamMemberDto item = raw.get(i);
            if (item == null) {
                continue;
            }
            String name = requireText(
                    item.name(), "TEAM_MEMBER_NAME_REQUIRED", "Each team member needs a name.");
            if (name.length() > MAX_TEAM_NAME) {
                throw new BusinessException("TEAM_MEMBER_NAME_TOO_LONG",
                        "Team member names must be at most " + MAX_TEAM_NAME + " characters.");
            }
            String responsibility = requireText(
                    item.responsibility(),
                    "TEAM_MEMBER_RESPONSIBILITY_REQUIRED",
                    "Each team member needs a responsibility.");
            if (responsibility.length() > MAX_TEAM_RESPONSIBILITY) {
                throw new BusinessException("TEAM_MEMBER_RESPONSIBILITY_TOO_LONG",
                        "Team member responsibilities must be at most "
                                + MAX_TEAM_RESPONSIBILITY + " characters.");
            }
            String imageUrl = blankToNull(item.imageUrl());
            if (imageUrl != null) {
                OwnedMediaUrlValidator.validate(imageUrl, userId);
            }
            UUID id = item.id() != null ? item.id() : UUID.randomUUID();
            int sortOrder = item.sortOrder() >= 0 ? item.sortOrder() : i;
            normalized.add(new ProfileTeamMemberDto(
                    id,
                    sortOrder,
                    name,
                    responsibility,
                    imageUrl,
                    normalizeTeamSocialLinks(item.socialLinks())
            ));
        }
        normalized.sort((a, b) -> Integer.compare(a.sortOrder(), b.sortOrder()));
        return List.copyOf(normalized);
    }

    public static List<ProfileGalleryItemDto> normalizeGalleryItems(
            List<ProfileGalleryItemDto> raw,
            UUID userId
    ) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        if (raw.size() > MAX_GALLERY_ITEMS) {
            throw new BusinessException("TOO_MANY_GALLERY_ITEMS",
                    "A maximum of " + MAX_GALLERY_ITEMS + " gallery items is allowed.");
        }
        List<ProfileGalleryItemDto> normalized = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            ProfileGalleryItemDto item = raw.get(i);
            if (item == null) {
                continue;
            }
            String title = blankToNull(item.title());
            if (title == null) {
                title = "";
            }
            if (title.length() > MAX_GALLERY_TITLE) {
                throw new BusinessException("GALLERY_TITLE_TOO_LONG",
                        "Gallery titles must be at most " + MAX_GALLERY_TITLE + " characters.");
            }
            String mediaUrl = requireText(
                    item.mediaUrl(), "GALLERY_MEDIA_URL_REQUIRED", "Each gallery item needs a media URL.");
            validateGalleryMediaUrl(mediaUrl, userId);
            String mediaType = normalizeGalleryMediaType(item.mediaType(), mediaUrl);
            UUID id = item.id() != null ? item.id() : UUID.randomUUID();
            int sortOrder = item.sortOrder() >= 0 ? item.sortOrder() : i;
            normalized.add(new ProfileGalleryItemDto(id, sortOrder, title, mediaUrl, mediaType));
        }
        normalized.sort((a, b) -> Integer.compare(a.sortOrder(), b.sortOrder()));
        return List.copyOf(normalized);
    }

    public static List<ProfileContactEntryDto> normalizeContactEntries(
            List<ProfileContactEntryDto> raw,
            int max,
            int maxValueLen,
            String codePrefix
    ) {
        return normalizeContactEntries(raw, max, maxValueLen, codePrefix, false);
    }

    public static List<ProfileContactEntryDto> normalizeContactEntries(
            List<ProfileContactEntryDto> raw,
            int max,
            int maxValueLen,
            String codePrefix,
            boolean validateEmail
    ) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        if (raw.size() > max) {
            throw new BusinessException(
                    "TOO_MANY_" + codePrefix,
                    "A maximum of " + max + " contact entries is allowed.");
        }
        List<ProfileContactEntryDto> normalized = new ArrayList<>();
        for (ProfileContactEntryDto item : raw) {
            if (item == null) {
                continue;
            }
            String value = blankToNull(item.value());
            if (value == null) {
                continue;
            }
            if (value.length() > maxValueLen) {
                throw new BusinessException(
                        codePrefix + "_TOO_LONG",
                        "Contact values must be at most " + maxValueLen + " characters.");
            }
            if (validateEmail && !value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw new BusinessException(
                        codePrefix + "_INVALID",
                        "Each contact email must be a valid email address.");
            }
            UUID id = item.id() != null ? item.id() : UUID.randomUUID();
            normalized.add(new ProfileContactEntryDto(id, normalized.size(), value));
        }
        if (normalized.size() > max) {
            throw new BusinessException(
                    "TOO_MANY_" + codePrefix,
                    "A maximum of " + max + " contact entries is allowed.");
        }
        return List.copyOf(normalized);
    }

    public static String firstContactValue(List<ProfileContactEntryDto> entries) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        for (ProfileContactEntryDto entry : entries) {
            if (entry == null) {
                continue;
            }
            String value = blankToNull(entry.value());
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public static List<ProfileContactEntryDto> contactEntriesFromLegacy(String legacy) {
        String value = blankToNull(legacy);
        if (value == null) {
            return List.of();
        }
        UUID id = UUID.nameUUIDFromBytes(("legacy-contact:" + value).getBytes(StandardCharsets.UTF_8));
        return List.of(new ProfileContactEntryDto(id, 0, value));
    }

    public static List<ProfileContactEntryDto> contactEntriesForResponse(
            List<ProfileContactEntryDto> stored,
            String legacy
    ) {
        if (stored != null && !stored.isEmpty()) {
            return List.copyOf(stored);
        }
        List<ProfileContactEntryDto> fromLegacy = contactEntriesFromLegacy(legacy);
        if (!fromLegacy.isEmpty()) {
            return fromLegacy;
        }
        return stored != null ? List.copyOf(stored) : List.of();
    }

    private static List<ProfileTeamSocialLinkDto> normalizeTeamSocialLinks(
            List<ProfileTeamSocialLinkDto> raw
    ) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        if (raw.size() > MAX_TEAM_SOCIAL_LINKS) {
            throw new BusinessException("TOO_MANY_TEAM_SOCIAL_LINKS",
                    "A maximum of " + MAX_TEAM_SOCIAL_LINKS + " social links is allowed per team member.");
        }
        List<ProfileTeamSocialLinkDto> normalized = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            ProfileTeamSocialLinkDto item = raw.get(i);
            if (item == null) {
                continue;
            }
            String platform = requireText(
                    item.platform(), "TEAM_SOCIAL_PLATFORM_REQUIRED", "Each team social link needs a platform.")
                    .toUpperCase(Locale.ROOT);
            if (!ALLOWED_TEAM_SOCIAL_PLATFORMS.contains(platform)) {
                throw new BusinessException("TEAM_SOCIAL_PLATFORM_INVALID",
                        "Team social link platform is invalid.");
            }
            String label = blankToNull(item.label());
            if (label != null && label.length() > MAX_TEAM_SOCIAL_LABEL) {
                throw new BusinessException("TEAM_SOCIAL_LABEL_TOO_LONG",
                        "Team social link labels must be at most "
                                + MAX_TEAM_SOCIAL_LABEL + " characters.");
            }
            String url = requireText(
                    item.url(), "TEAM_SOCIAL_URL_REQUIRED", "Each team social link needs a URL.");
            if (url.length() > MAX_LINK_URL) {
                throw new BusinessException("TEAM_SOCIAL_URL_TOO_LONG",
                        "Team social link URLs must be at most " + MAX_LINK_URL + " characters.");
            }
            if ("EMAIL".equals(platform)) {
                validateEmailLink(url);
            } else {
                validateSafeUrl(url);
            }
            UUID id = item.id() != null ? item.id() : UUID.randomUUID();
            int sortOrder = item.sortOrder() >= 0 ? item.sortOrder() : i;
            normalized.add(new ProfileTeamSocialLinkDto(id, platform, label, url, sortOrder));
        }
        normalized.sort((a, b) -> Integer.compare(a.sortOrder(), b.sortOrder()));
        return List.copyOf(normalized);
    }

    private static void validateEmailLink(String raw) {
        String email = raw.regionMatches(true, 0, "mailto:", 0, 7) ? raw.substring(7) : raw;
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new BusinessException("TEAM_SOCIAL_EMAIL_INVALID",
                    "Email links must contain an email address or a mailto URL.");
        }
    }

    private static String normalizeGalleryMediaType(String raw, String mediaUrl) {
        String value = blankToNull(raw);
        if (value == null) {
            return inferGalleryMediaType(mediaUrl);
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if (!"IMAGE".equals(upper) && !"VIDEO".equals(upper)) {
            throw new BusinessException("GALLERY_MEDIA_TYPE_INVALID",
                    "Gallery media type must be IMAGE or VIDEO.");
        }
        return upper;
    }

    private static String inferGalleryMediaType(String mediaUrl) {
        String path;
        try {
            path = URI.create(mediaUrl).getPath();
        } catch (IllegalArgumentException e) {
            return "IMAGE";
        }
        if (path == null) {
            return "IMAGE";
        }
        int dot = path.lastIndexOf('.');
        if (dot >= 0 && dot < path.length() - 1
                && VIDEO_EXTENSIONS.contains(path.substring(dot + 1).toLowerCase(Locale.ROOT))) {
            return "VIDEO";
        }
        return "IMAGE";
    }

    public static List<ProfileServiceDto> normalizeServices(List<ProfileServiceDto> raw) {
        return normalizeServices(raw, null);
    }

    public static List<ProfileServiceDto> normalizeServices(
            List<ProfileServiceDto> raw,
            List<String> allowedSpecialties) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        if (raw.size() > MAX_SERVICES) {
            throw new BusinessException("TOO_MANY_SERVICES",
                    "A maximum of " + MAX_SERVICES + " services is allowed.");
        }
        List<ProfileServiceDto> normalized = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            ProfileServiceDto item = raw.get(i);
            if (item == null) {
                continue;
            }
            String title = requireText(item.title(), "SERVICE_TITLE_REQUIRED", "Each service needs a title.");
            if (title.length() > MAX_SERVICE_TITLE) {
                throw new BusinessException("SERVICE_TITLE_TOO_LONG",
                        "Service titles must be at most " + MAX_SERVICE_TITLE + " characters.");
            }
            String description = blankToNull(item.description());
            if (description != null && description.length() > MAX_SERVICE_DESC) {
                throw new BusinessException("SERVICE_DESC_TOO_LONG",
                        "Service descriptions must be at most " + MAX_SERVICE_DESC + " characters.");
            }
            String deadline = blankToNull(item.deadline());
            if (deadline != null && deadline.length() > MAX_SERVICE_DEADLINE) {
                throw new BusinessException("SERVICE_DEADLINE_TOO_LONG",
                        "Service deadlines must be at most " + MAX_SERVICE_DEADLINE + " characters.");
            }
            String pricingType = normalizePricingType(item.pricingType(), item.basePriceCents());
            Integer basePriceCents = item.basePriceCents();
            if ("QUOTE".equals(pricingType)) {
                basePriceCents = null;
            } else if (basePriceCents == null) {
                throw new BusinessException("SERVICE_PRICE_REQUIRED",
                        "A price is required unless pricing is \"Sur devis\".");
            } else if (basePriceCents < 0) {
                throw new BusinessException("SERVICE_PRICE_INVALID", "Service base price cannot be negative.");
            }
            List<String> tasks = normalizeServiceTasks(item.tasks());
            String specialty = resolveServiceSpecialty(item.specialty(), allowedSpecialties);
            String coverImageUrl = blankToNull(item.coverImageUrl());
            if (coverImageUrl != null && coverImageUrl.length() > MAX_SERVICE_COVER_URL) {
                throw new BusinessException("SERVICE_COVER_TOO_LONG",
                        "Cover image URL must be at most " + MAX_SERVICE_COVER_URL + " characters.");
            }
            String status = normalizeServiceStatus(item.status());
            List<String> tags = normalizeServiceTags(item.tags());
            String currency = normalizeServiceCurrency(item.currency());
            Integer deliveryValue = item.deliveryValue();
            String deliveryUnit = normalizeDeliveryUnit(item.deliveryUnit());
            if (deliveryValue != null && deliveryValue < 1) {
                throw new BusinessException("SERVICE_DELIVERY_INVALID",
                        "Delivery time must be at least 1.");
            }
            if (deliveryValue != null && deliveryUnit == null) {
                deliveryUnit = "DAYS";
            }
            if (deliveryValue == null) {
                deliveryUnit = null;
            }
            // Keep a human deadline string for older clients; prefer structured fields when present.
            String resolvedDeadline = deadline;
            if (deliveryValue != null && deliveryUnit != null) {
                String unitLabel = "WEEKS".equals(deliveryUnit) ? "weeks" : "days";
                if (deliveryValue == 1) {
                    unitLabel = "WEEKS".equals(deliveryUnit) ? "week" : "day";
                }
                resolvedDeadline = deliveryValue + " " + unitLabel;
            } else if (deadline != null) {
                int[] parsed = parseLegacyDeadline(deadline);
                if (parsed != null) {
                    deliveryValue = parsed[0];
                    deliveryUnit = parsed[1] == 1 ? "WEEKS" : "DAYS";
                }
            }
            UUID id = item.id() != null ? item.id() : UUID.randomUUID();
            int sortOrder = item.sortOrder() >= 0 ? item.sortOrder() : i;
            normalized.add(new ProfileServiceDto(
                    id, sortOrder, title, description, basePriceCents, resolvedDeadline, tasks,
                    specialty, pricingType, coverImageUrl, status, tags,
                    currency, deliveryValue, deliveryUnit));
        }
        normalized.sort((a, b) -> Integer.compare(a.sortOrder(), b.sortOrder()));
        return List.copyOf(normalized);
    }

    public static List<ProfileServiceDto> activeServices(List<ProfileServiceDto> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<ProfileServiceDto> active = new ArrayList<>();
        for (ProfileServiceDto item : raw) {
            if (item == null) {
                continue;
            }
            String status = normalizeServiceStatus(item.status());
            if ("ACTIVE".equals(status)) {
                active.add(item);
            }
        }
        return List.copyOf(active);
    }

    public static long countActiveServices(List<ProfileServiceDto> raw) {
        return activeServices(raw).size();
    }

    static String normalizeServiceCurrency(String raw) {
        if (raw == null || raw.isBlank()) {
            return "MGA";
        }
        String code = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (code.isEmpty() || code.length() > 8) {
            throw new BusinessException("SERVICE_CURRENCY_INVALID",
                    "Currency code must be 1–8 alphanumeric characters.");
        }
        return code;
    }

    static String normalizeDeliveryUnit(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT);
        return switch (key) {
            case "DAY", "DAYS", "JOUR", "JOURS" -> "DAYS";
            case "WEEK", "WEEKS", "SEMAINE", "SEMAINES" -> "WEEKS";
            default -> throw new BusinessException("SERVICE_DELIVERY_UNIT_INVALID",
                    "Delivery unit must be days or weeks.");
        };
    }

    /** Returns [value, unitFlag] where unitFlag 0=DAYS, 1=WEEKS; null if unparsable. */
    static int[] parseLegacyDeadline(String deadline) {
        if (deadline == null || deadline.isBlank()) {
            return null;
        }
        String trimmed = deadline.trim().toLowerCase(Locale.ROOT);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(\\d+)\\s*(day|days|jour|jours|week|weeks|semaine|semaines)?$")
                .matcher(trimmed);
        if (!m.matches()) {
            return null;
        }
        int value = Integer.parseInt(m.group(1));
        if (value < 1) {
            return null;
        }
        String unit = m.group(2);
        boolean weeks = unit != null && (unit.startsWith("week") || unit.startsWith("semaine"));
        return new int[]{value, weeks ? 1 : 0};
    }

    static String normalizePricingType(String raw, Integer basePriceCents) {
        if (raw == null || raw.isBlank()) {
            return basePriceCents != null ? "FIXED" : "QUOTE";
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (key) {
            case "FIXED", "FIXE", "FIXED_PRICE" -> "FIXED";
            case "FROM", "A_PARTIR_DE", "STARTING_AT", "STARTING" -> "FROM";
            case "QUOTE", "SUR_DEVIS", "ON_REQUEST", "DEVIS" -> "QUOTE";
            default -> throw new BusinessException("SERVICE_PRICING_INVALID",
                    "Pricing type must be fixe, à partir de, or sur devis.");
        };
    }

    static String normalizeServiceStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return "ACTIVE";
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (key) {
            case "ACTIVE", "ACTIF" -> "ACTIVE";
            case "PAUSED", "EN_PAUSE", "PAUSE" -> "PAUSED";
            case "ARCHIVED", "ARCHIVE", "ARCHIVÉ" -> "ARCHIVED";
            default -> throw new BusinessException("SERVICE_STATUS_INVALID",
                    "Service status must be actif, en pause, or archivé.");
        };
    }

    static String resolveServiceSpecialty(String raw, List<String> allowedSpecialties) {
        if (allowedSpecialties == null) {
            String trimmed = blankToNull(raw);
            return trimmed;
        }
        if (allowedSpecialties.isEmpty()) {
            throw new BusinessException("SERVICE_SPECIALTY_REQUIRED",
                    "Add at least one specialty to your profile before creating a service.");
        }
        String matched = SpecialtyTaxonomy.matchAllowed(raw, allowedSpecialties);
        if (matched == null) {
            throw new BusinessException("SERVICE_SPECIALTY_INVALID",
                    "Each service must use one of your profile specialties.");
        }
        return matched;
    }

    static List<String> normalizeServiceTags(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> tags = new ArrayList<>();
        for (String item : raw) {
            String value = blankToNull(item);
            if (value == null) {
                continue;
            }
            if (value.length() > MAX_SERVICE_TAG_LENGTH) {
                value = value.substring(0, MAX_SERVICE_TAG_LENGTH).trim();
            }
            String key = value.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                continue;
            }
            tags.add(value);
            if (tags.size() >= MAX_SERVICE_TAGS) {
                break;
            }
        }
        return List.copyOf(tags);
    }

    static List<String> normalizeServiceTasks(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        if (raw.size() > MAX_SERVICE_TASKS) {
            throw new BusinessException("TOO_MANY_SERVICE_TASKS",
                    "Each service can have at most " + MAX_SERVICE_TASKS + " tasks.");
        }
        List<String> normalized = new ArrayList<>();
        for (String task : raw) {
            String value = blankToNull(task);
            if (value == null) {
                continue;
            }
            if (value.length() > MAX_SERVICE_TASK) {
                throw new BusinessException("SERVICE_TASK_TOO_LONG",
                        "Service tasks must be at most " + MAX_SERVICE_TASK + " characters.");
            }
            normalized.add(value);
        }
        return List.copyOf(normalized);
    }

    public static List<ProfileLinkDto> normalizeLinks(List<ProfileLinkDto> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        if (raw.size() > MAX_LINKS) {
            throw new BusinessException("TOO_MANY_LINKS",
                    "A maximum of " + MAX_LINKS + " links is allowed.");
        }
        List<ProfileLinkDto> normalized = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            ProfileLinkDto item = raw.get(i);
            if (item == null) {
                continue;
            }
            String type = item.type() != null ? item.type().trim().toUpperCase(Locale.ROOT) : "CUSTOM";
            if (!ALLOWED_LINK_TYPES.contains(type)) {
                throw new BusinessException("LINK_TYPE_INVALID",
                        "Link type must be one of: WEBSITE, SOCIAL, CTA, CUSTOM.");
            }
            String url = requireText(item.url(), "LINK_URL_REQUIRED", "Each link needs a URL.");
            validateSafeUrl(url);
            if (url.length() > MAX_LINK_URL) {
                throw new BusinessException("LINK_URL_TOO_LONG",
                        "Link URLs must be at most " + MAX_LINK_URL + " characters.");
            }
            String label = blankToNull(item.label());
            if (label != null && label.length() > MAX_LINK_LABEL) {
                throw new BusinessException("LINK_LABEL_TOO_LONG",
                        "Link labels must be at most " + MAX_LINK_LABEL + " characters.");
            }
            String platform = blankToNull(item.platform());
            UUID id = item.id() != null ? item.id() : UUID.randomUUID();
            int sortOrder = item.sortOrder() >= 0 ? item.sortOrder() : i;
            normalized.add(new ProfileLinkDto(id, type, label, url, sortOrder, platform));
        }
        normalized.sort((a, b) -> Integer.compare(a.sortOrder(), b.sortOrder()));
        return List.copyOf(normalized);
    }

    static void validateSafeUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("LINK_URL_INVALID", "Link URL is invalid.");
        }
        String scheme = uri.getScheme();
        if (scheme == null || scheme.isBlank()) {
            throw new BusinessException("LINK_URL_INVALID", "Link URL must include http or https.");
        }
        String lower = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(lower) && !"https".equals(lower)) {
            throw new BusinessException("LINK_URL_UNSAFE", "Only http and https URLs are allowed.");
        }
    }

    private static void validateGalleryMediaUrl(String mediaUrl, UUID userId) {
        if (!mediaUrl.startsWith("http://") && !mediaUrl.startsWith("https://")) {
            throw new BusinessException(
                    "GALLERY_MEDIA_URL_INVALID", "Media URL must use http or https.");
        }
        try {
            OwnedMediaUrlValidator.validate(mediaUrl, userId);
        } catch (BusinessException ex) {
            // Gallery also accepts external http(s) URLs pasted by the creator.
            if ("BLOCK_MEDIA_URL_FORBIDDEN".equals(ex.getCode())) {
                return;
            }
            throw ex;
        }
    }

    private static String requireText(String value, String code, String message) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            throw new BusinessException(code, message);
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
