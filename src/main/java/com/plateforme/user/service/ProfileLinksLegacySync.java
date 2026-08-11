package com.plateforme.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plateforme.marketplace.dto.SocialLink;
import com.plateforme.marketplace.util.SocialLinksJsonParser;
import com.plateforme.user.dto.ProfileLinkDto;
import com.plateforme.user.entity.CreatorProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class ProfileLinksLegacySync {

    private ProfileLinksLegacySync() {}

    static List<ProfileLinkDto> mergeLinksFromLegacy(
            List<ProfileLinkDto> profileLinks,
            String websiteUrl,
            String socialLinksJson,
            String ctaLabel,
            String ctaUrl,
            ObjectMapper objectMapper) {
        if (profileLinks != null && !profileLinks.isEmpty()) {
            return profileLinks;
        }
        List<ProfileLinkDto> merged = new ArrayList<>();
        int order = 0;
        String website = blankToNull(websiteUrl);
        if (website != null) {
            merged.add(new ProfileLinkDto(UUID.randomUUID(), "WEBSITE", "Site web", website, order++, null));
        }
        String cta = blankToNull(ctaUrl);
        if (cta != null) {
            String label = blankToNull(ctaLabel);
            merged.add(new ProfileLinkDto(
                    UUID.randomUUID(), "CTA", label != null ? label : "Lien principal", cta, order++, null));
        }
        for (SocialLink social : SocialLinksJsonParser.parse(objectMapper, socialLinksJson)) {
            if (social.url() == null || social.url().isBlank()) {
                continue;
            }
            merged.add(new ProfileLinkDto(
                    UUID.randomUUID(),
                    "SOCIAL",
                    blankToNull(social.platform()),
                    social.url().trim(),
                    order++,
                    blankToNull(social.platform())));
        }
        return merged;
    }

    static void syncLegacyColumns(CreatorProfile profile, ObjectMapper objectMapper) {
        List<ProfileLinkDto> links = profile.getProfileLinks() != null ? profile.getProfileLinks() : List.of();
        profile.setWebsiteUrl(null);
        profile.setCtaLabel(null);
        profile.setCtaUrl(null);
        profile.setSocialLinks(null);

        List<SocialLink> socials = new ArrayList<>();
        for (ProfileLinkDto link : links) {
            if (link == null || link.url() == null || link.url().isBlank()) {
                continue;
            }
            String type = link.type() != null ? link.type().toUpperCase(Locale.ROOT) : "CUSTOM";
            switch (type) {
                case "WEBSITE" -> {
                    if (profile.getWebsiteUrl() == null) {
                        profile.setWebsiteUrl(link.url().trim());
                    }
                }
                case "CTA" -> {
                    if (profile.getCtaUrl() == null) {
                        profile.setCtaUrl(link.url().trim());
                        profile.setCtaLabel(link.label());
                    }
                }
                case "SOCIAL" -> socials.add(new SocialLink(
                        link.platform() != null ? link.platform() : link.label(), link.url().trim()));
                default -> { }
            }
        }
        if (!socials.isEmpty()) {
            try {
                profile.setSocialLinks(objectMapper.writeValueAsString(socials));
            } catch (Exception ignored) {
                profile.setSocialLinks("[]");
            }
        }

        List<String> spoken = profile.getSpokenLanguages() != null ? profile.getSpokenLanguages() : List.of();
        if (!spoken.isEmpty()) {
            profile.setLanguages(String.join(", ", spoken));
        } else if (profile.getLanguages() == null) {
            profile.setLanguages(null);
        }
    }

    static List<String> mergeSpokenLanguages(List<String> spokenLanguages, String legacyLanguages) {
        if (spokenLanguages != null && !spokenLanguages.isEmpty()) {
            return spokenLanguages;
        }
        if (legacyLanguages == null || legacyLanguages.isBlank()) {
            return null;
        }
        return List.of(legacyLanguages.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
