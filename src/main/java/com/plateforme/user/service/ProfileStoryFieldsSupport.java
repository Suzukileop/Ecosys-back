package com.plateforme.user.service;

import com.plateforme.shared.exception.BusinessException;
import com.plateforme.shared.util.OwnedMediaUrlValidator;
import com.plateforme.user.dto.ExperienceProofLink;
import com.plateforme.user.dto.ProfileMediaBlock;
import com.plateforme.user.dto.ProfileStrengthToolDto;
import com.plateforme.user.dto.ProfileToolRefDto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class ProfileStoryFieldsSupport {

    static final int MAX_BLOCKS = 50;
    static final int MAX_BLOCK_TEXT = 4000;
    static final int MAX_SUBTITLES_PER_BLOCK = 10;
    static final int MAX_SUBTITLE_LENGTH = 500;
    static final int MAX_PERIOD_LENGTH = 80;
    static final int MAX_TITLE_LENGTH = 200;
    static final int MAX_ORGANIZATION_LENGTH = 120;
    static final int MAX_STRENGTHS = 12;
    static final int MAX_STRENGTH_LENGTH = 80;
    static final int MAX_STRENGTH_DESCRIPTION_LENGTH = 280;
    static final int MAX_STRENGTH_CATEGORY_LENGTH = 80;
    static final int MAX_STRENGTH_USE_CASES = 8;
    static final int MAX_STRENGTH_USE_CASE_LENGTH = 60;
    static final int MAX_STRENGTH_EXPERIENCE_YEARS = 40;
    static final int MAX_STRENGTH_EXPERIENCE_LABEL_LENGTH = 80;
    static final int MIN_YEARS = 0;
    static final int MAX_YEARS = 80;

    static final int MAX_TASKS_PER_BLOCK = 12;
    static final int MAX_TASK_LENGTH = 300;
    static final int MAX_TOOLS_PER_BLOCK = 8;
    static final int MAX_TOOL_LENGTH = 80;
    static final int MAX_LINKS_PER_BLOCK = 5;
    static final int MAX_LINK_LABEL = 100;
    static final int MAX_LINK_URL = 500;
    static final int MAX_REMARKS_LENGTH = 500;
    static final int MAX_LOCATION_LENGTH = 120;

    private static final Set<String> ALLOWED_STATUS = Set.of("ONGOING", "FINISHED");
    private static final Set<String> ALLOWED_EMPLOYMENT = Set.of(
            "FULL_TIME", "PART_TIME", "CONTRACT", "FREELANCE", "INTERNSHIP"
    );
    private static final Set<String> ALLOWED_PROOF_PLATFORMS = Set.of(
            "GITHUB", "FACEBOOK", "LINKEDIN", "INSTAGRAM", "YOUTUBE", "WEBSITE", "OTHER"
    );
    private static final Set<String> ALLOWED_STRENGTH_LEVELS = Set.of(
            "beginner", "intermediate", "advanced", "expert"
    );

    private ProfileStoryFieldsSupport() {}

    public static List<ProfileMediaBlock> normalizeBlocks(List<ProfileMediaBlock> raw, UUID userId) {
        if (raw == null) {
            return List.of();
        }
        if (raw.size() > MAX_BLOCKS) {
            throw new BusinessException("TOO_MANY_BLOCKS",
                    "A maximum of " + MAX_BLOCKS + " blocks is allowed.");
        }
        List<ProfileMediaBlock> normalized = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            ProfileMediaBlock block = raw.get(i);
            if (block == null) {
                continue;
            }
            String text = block.text() != null ? block.text().trim() : "";
            if (text.isEmpty()) {
                throw new BusinessException("BLOCK_TEXT_REQUIRED", "Each block must include text.");
            }
            if (text.length() > MAX_BLOCK_TEXT) {
                throw new BusinessException("BLOCK_TEXT_TOO_LONG",
                        "Block text must be at most " + MAX_BLOCK_TEXT + " characters.");
            }
            String mediaUrl = blankToNull(block.mediaUrl());
            String mediaType = blankToNull(block.mediaType());
            if ((mediaUrl == null) != (mediaType == null)) {
                throw new BusinessException("BLOCK_MEDIA_INCOMPLETE",
                        "Media URL and media type must both be set or both be empty.");
            }
            if (mediaType != null && !"IMAGE".equals(mediaType) && !"VIDEO".equals(mediaType)) {
                throw new BusinessException("BLOCK_MEDIA_TYPE_INVALID",
                        "Media type must be IMAGE or VIDEO.");
            }
            if (mediaUrl != null) {
                OwnedMediaUrlValidator.validate(mediaUrl, userId);
            }
            UUID id = block.id() != null ? block.id() : UUID.randomUUID();
            int sortOrder = block.sortOrder() >= 0 ? block.sortOrder() : i;
            String title = normalizeOptionalLabel(block.title(), MAX_TITLE_LENGTH, "TITLE_TOO_LONG", "Title");
            String organization = normalizeOptionalLabel(
                    block.organization(), MAX_ORGANIZATION_LENGTH, "ORGANIZATION_TOO_LONG", "Organization");
            String period = normalizePeriod(block.period());
            List<String> subtitles = normalizeSubtitles(block.subtitles());
            String status = normalizeStatus(block.status());
            List<String> tasks = normalizeTasks(block.tasks());
            List<ProfileToolRefDto> tools = normalizeTools(block.tools(), userId);
            List<ExperienceProofLink> links = normalizeProofLinks(block.links());
            String remarks = normalizeOptionalLabel(
                    block.remarks(), MAX_REMARKS_LENGTH, "REMARKS_TOO_LONG", "Remarks");
            String location = normalizeOptionalLabel(
                    block.location(), MAX_LOCATION_LENGTH, "LOCATION_TOO_LONG", "Location");
            String employmentType = normalizeEmploymentType(block.employmentType());
            normalized.add(new ProfileMediaBlock(
                    id, sortOrder, title, organization, text, mediaUrl, mediaType, period, subtitles,
                    status, tasks, tools, links, remarks, location, employmentType));
        }
        normalized.sort((a, b) -> Integer.compare(a.sortOrder(), b.sortOrder()));
        return List.copyOf(normalized);
    }

    /**
     * Why choose me is text-only: drop media before validation/normalization.
     */
    public static List<ProfileMediaBlock> normalizeWhyMeBlocks(List<ProfileMediaBlock> raw, UUID userId) {
        if (raw == null) {
            return List.of();
        }
        List<ProfileMediaBlock> withoutMedia = new ArrayList<>(raw.size());
        for (ProfileMediaBlock block : raw) {
            if (block == null) {
                continue;
            }
            withoutMedia.add(new ProfileMediaBlock(
                    block.id(),
                    block.sortOrder(),
                    block.title(),
                    block.organization(),
                    block.text(),
                    null,
                    null,
                    block.period(),
                    block.subtitles(),
                    block.status(),
                    block.tasks(),
                    block.tools(),
                    block.links(),
                    block.remarks(),
                    block.location(),
                    block.employmentType()
            ));
        }
        return normalizeBlocks(withoutMedia, userId);
    }

    /** Strip legacy media from Why choose me blocks for API responses. */
    public static List<ProfileMediaBlock> stripWhyMeMedia(List<ProfileMediaBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return blocks == null ? List.of() : List.copyOf(blocks);
        }
        List<ProfileMediaBlock> stripped = new ArrayList<>(blocks.size());
        for (ProfileMediaBlock block : blocks) {
            if (block == null) {
                continue;
            }
            if (block.mediaUrl() == null && block.mediaType() == null) {
                stripped.add(block);
                continue;
            }
            stripped.add(new ProfileMediaBlock(
                    block.id(),
                    block.sortOrder(),
                    block.title(),
                    block.organization(),
                    block.text(),
                    null,
                    null,
                    block.period(),
                    block.subtitles(),
                    block.status(),
                    block.tasks(),
                    block.tools(),
                    block.links(),
                    block.remarks(),
                    block.location(),
                    block.employmentType()
            ));
        }
        return List.copyOf(stripped);
    }

    static String normalizePeriod(String raw) {
        return normalizeOptionalLabel(raw, MAX_PERIOD_LENGTH, "PERIOD_TOO_LONG", "Period");
    }

    static String normalizeOptionalLabel(String raw, int maxLength, String errorCode, String label) {
        String value = blankToNull(raw);
        if (value != null && value.length() > maxLength) {
            throw new BusinessException(errorCode,
                    label + " must be at most " + maxLength + " characters.");
        }
        return value;
    }

    static String normalizeStatus(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUS.contains(upper)) {
            throw new BusinessException("BLOCK_STATUS_INVALID",
                    "Status must be ONGOING or FINISHED.");
        }
        return upper;
    }

    static String normalizeEmploymentType(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if (!ALLOWED_EMPLOYMENT.contains(upper)) {
            throw new BusinessException("BLOCK_EMPLOYMENT_TYPE_INVALID",
                    "Employment type must be FULL_TIME, PART_TIME, CONTRACT, FREELANCE, or INTERNSHIP.");
        }
        return upper;
    }

    public static List<String> normalizeSubtitles(List<String> raw) {
        return normalizeStringList(
                raw,
                MAX_SUBTITLES_PER_BLOCK,
                MAX_SUBTITLE_LENGTH,
                "TOO_MANY_SUBTITLES",
                "SUBTITLE_TOO_LONG",
                "subtitles",
                "subtitle"
        );
    }

    static List<String> normalizeTasks(List<String> raw) {
        return normalizeStringList(
                raw,
                MAX_TASKS_PER_BLOCK,
                MAX_TASK_LENGTH,
                "TOO_MANY_TASKS",
                "TASK_TOO_LONG",
                "tasks",
                "task"
        );
    }

    static List<ProfileToolRefDto> normalizeTools(List<ProfileToolRefDto> raw, UUID userId) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<ProfileToolRefDto> normalized = new ArrayList<>();
        Set<String> uniqueKeys = new LinkedHashSet<>();
        for (ProfileToolRefDto item : raw) {
            if (item == null) {
                continue;
            }
            String name = item.name() != null ? item.name().trim() : "";
            if (name.isEmpty()) {
                continue;
            }
            if (name.length() > MAX_TOOL_LENGTH) {
                throw new BusinessException("TOOL_TOO_LONG",
                        "Each tool must be at most " + MAX_TOOL_LENGTH + " characters.");
            }
            String key = name.toLowerCase(Locale.ROOT);
            if (!uniqueKeys.add(key)) {
                continue;
            }
            String iconUrl = blankToNull(item.iconUrl());
            if (iconUrl != null) {
                OwnedMediaUrlValidator.validate(iconUrl, userId);
            }
            normalized.add(new ProfileToolRefDto(name, iconUrl));
            if (normalized.size() > MAX_TOOLS_PER_BLOCK) {
                throw new BusinessException("TOO_MANY_TOOLS",
                        "A maximum of " + MAX_TOOLS_PER_BLOCK + " tools is allowed.");
            }
        }
        return List.copyOf(normalized);
    }

    private static List<String> normalizeStringList(
            List<String> raw,
            int maxItems,
            int maxLength,
            String tooManyCode,
            String tooLongCode,
            String pluralLabel,
            String singularLabel
    ) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String item : raw) {
            if (item == null) {
                continue;
            }
            String trimmed = item.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > maxLength) {
                throw new BusinessException(tooLongCode,
                        "Each " + singularLabel + " must be at most " + maxLength + " characters.");
            }
            normalized.add(trimmed);
            if (normalized.size() > maxItems) {
                throw new BusinessException(tooManyCode,
                        "A maximum of " + maxItems + " " + pluralLabel + " is allowed.");
            }
        }
        return List.copyOf(normalized);
    }

    static List<ExperienceProofLink> normalizeProofLinks(List<ExperienceProofLink> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        if (raw.size() > MAX_LINKS_PER_BLOCK) {
            throw new BusinessException("TOO_MANY_PROOF_LINKS",
                    "A maximum of " + MAX_LINKS_PER_BLOCK + " proof links is allowed per experience.");
        }
        List<ExperienceProofLink> normalized = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            ExperienceProofLink item = raw.get(i);
            if (item == null) {
                continue;
            }
            String url = blankToNull(item.url());
            String label = blankToNull(item.label());
            if (url == null && label == null) {
                continue;
            }
            if (url == null) {
                throw new BusinessException("PROOF_LINK_URL_REQUIRED", "Each proof link needs a URL.");
            }
            ProfileExtensionsSupport.validateSafeUrl(url);
            if (url.length() > MAX_LINK_URL) {
                throw new BusinessException("PROOF_LINK_URL_TOO_LONG",
                        "Proof link URLs must be at most " + MAX_LINK_URL + " characters.");
            }
            if (label == null) {
                throw new BusinessException("PROOF_LINK_LABEL_REQUIRED", "Each proof link needs a label.");
            }
            if (label.length() > MAX_LINK_LABEL) {
                throw new BusinessException("PROOF_LINK_LABEL_TOO_LONG",
                        "Proof link labels must be at most " + MAX_LINK_LABEL + " characters.");
            }
            String platform = blankToNull(item.platform());
            if (platform != null) {
                platform = platform.toUpperCase(Locale.ROOT);
                if (!ALLOWED_PROOF_PLATFORMS.contains(platform)) {
                    throw new BusinessException("PROOF_LINK_PLATFORM_INVALID",
                            "Proof link platform is invalid.");
                }
            }
            UUID id = item.id() != null ? item.id() : UUID.randomUUID();
            int sortOrder = item.sortOrder() >= 0 ? item.sortOrder() : i;
            normalized.add(new ExperienceProofLink(id, label, url, platform, sortOrder));
        }
        normalized.sort((a, b) -> Integer.compare(a.sortOrder(), b.sortOrder()));
        return List.copyOf(normalized);
    }

    static List<ProfileStrengthToolDto> normalizeStrengths(List<ProfileStrengthToolDto> raw, UUID userId) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<ProfileStrengthToolDto> normalized = new ArrayList<>();
        Set<String> uniqueKeys = new LinkedHashSet<>();
        for (ProfileStrengthToolDto item : raw) {
            if (item == null) {
                continue;
            }
            String name = item.name() != null ? item.name().trim() : "";
            if (name.isEmpty()) {
                continue;
            }
            if (name.length() > MAX_STRENGTH_LENGTH) {
                throw new BusinessException("STRENGTH_TOO_LONG",
                        "Each strength must be at most " + MAX_STRENGTH_LENGTH + " characters.");
            }
            String key = name.toLowerCase(Locale.ROOT);
            if (!uniqueKeys.add(key)) {
                continue;
            }
            String description = blankToNull(item.description());
            if (description != null && description.length() > MAX_STRENGTH_DESCRIPTION_LENGTH) {
                throw new BusinessException("STRENGTH_DESCRIPTION_TOO_LONG",
                        "Each strength description must be at most "
                                + MAX_STRENGTH_DESCRIPTION_LENGTH + " characters.");
            }
            String category = truncate(blankToNull(item.category()), MAX_STRENGTH_CATEGORY_LENGTH);
            String level = blankToNull(item.level());
            if (level != null) {
                level = level.toLowerCase(Locale.ROOT);
                if (!ALLOWED_STRENGTH_LEVELS.contains(level)) {
                    throw new BusinessException("STRENGTH_LEVEL_INVALID",
                            "Strength level must be beginner, intermediate, advanced, or expert.");
                }
            }
            List<String> useCases = normalizeStrengthUseCases(item.useCases());
            Integer experienceYears = item.experienceYears() == null
                    ? null
                    : Math.max(0, Math.min(MAX_STRENGTH_EXPERIENCE_YEARS, item.experienceYears()));
            String experienceLabel = truncate(
                    blankToNull(item.experienceLabel()),
                    MAX_STRENGTH_EXPERIENCE_LABEL_LENGTH
            );
            Boolean currentlyUsed = item.currentlyUsed() == null
                    ? null
                    : Boolean.TRUE.equals(item.currentlyUsed());
            String iconUrl = blankToNull(item.iconUrl());
            if (iconUrl != null) {
                OwnedMediaUrlValidator.validate(iconUrl, userId);
            }
            normalized.add(new ProfileStrengthToolDto(
                    name,
                    description,
                    category,
                    level,
                    useCases,
                    experienceYears,
                    experienceLabel,
                    currentlyUsed,
                    iconUrl
            ));
            if (normalized.size() > MAX_STRENGTHS) {
                throw new BusinessException("TOO_MANY_STRENGTHS",
                        "A maximum of " + MAX_STRENGTHS + " strengths is allowed.");
            }
        }
        return List.copyOf(normalized);
    }

    private static List<String> normalizeStrengthUseCases(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        Set<String> uniqueKeys = new LinkedHashSet<>();
        for (String item : raw) {
            String value = truncate(blankToNull(item), MAX_STRENGTH_USE_CASE_LENGTH);
            if (value == null || !uniqueKeys.add(value.toLowerCase(Locale.ROOT))) {
                continue;
            }
            normalized.add(value);
            if (normalized.size() == MAX_STRENGTH_USE_CASES) {
                break;
            }
        }
        return List.copyOf(normalized);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    static Integer normalizeYears(Integer years) {
        if (years == null) {
            return null;
        }
        if (years < MIN_YEARS || years > MAX_YEARS) {
            throw new BusinessException("YEARS_OUT_OF_RANGE",
                    "Years of experience must be between " + MIN_YEARS + " and " + MAX_YEARS + ".");
        }
        return years;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    static boolean hasBlockContent(List<ProfileMediaBlock> blocks) {
        return blocks != null && !blocks.isEmpty();
    }

    static boolean hasStrengths(List<ProfileStrengthToolDto> strengths) {
        return strengths != null && strengths.stream()
                .anyMatch(s -> s != null && s.name() != null && !s.name().isBlank());
    }
}
