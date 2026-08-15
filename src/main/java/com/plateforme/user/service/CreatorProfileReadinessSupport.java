package com.plateforme.user.service;

import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.dto.ProfileContactEntryDto;
import com.plateforme.user.dto.ProfileLinkDto;
import com.plateforme.user.dto.ProfileServiceDto;
import com.plateforme.user.entity.CreatorProfile;
import com.plateforme.user.entity.User;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Server-side mirror of frontend profile readiness:
 * photo, address, phone, email, nationality, link, name, role, location;
 * specialties required for services.
 */
public final class CreatorProfileReadinessSupport {

    public static final String CODE_PROFILE_INCOMPLETE = "PROFILE_INCOMPLETE";

    private CreatorProfileReadinessSupport() {
    }

    public enum Field {
        PHOTO("photo"),
        ADDRESS("address"),
        PHONE("phone"),
        EMAIL("email"),
        NATIONALITY("nationality"),
        LINK("link"),
        NAME("name"),
        ROLE("role"),
        LOCATION("location"),
        SPECIALTIES("specialties");

        private final String key;

        Field(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    public static List<Field> missingFields(
            User user,
            CreatorProfile profile,
            boolean requireSpecialties) {
        List<Field> missing = new ArrayList<>();
        if (user == null || !isUploadedProfilePhoto(user.getAvatarUrl())) {
            missing.add(Field.PHOTO);
        }
        if (profile == null || !hasContactValue(profile.getContactAddress(), profile.getContactAddresses())) {
            missing.add(Field.ADDRESS);
        }
        if (profile == null || !hasContactValue(profile.getContactPhone(), profile.getContactPhones())) {
            missing.add(Field.PHONE);
        }
        if (profile == null || !hasContactValue(profile.getContactEmail(), profile.getContactEmails())) {
            missing.add(Field.EMAIL);
        }
        if (profile == null || !hasText(profile.getNationality())) {
            missing.add(Field.NATIONALITY);
        }
        if (profile == null || !hasLink(profile)) {
            missing.add(Field.LINK);
        }
        if (user == null || !hasText(user.getFullName())) {
            missing.add(Field.NAME);
        }
        if (profile == null || !hasText(profile.getAppRole())) {
            missing.add(Field.ROLE);
        }
        if (profile == null || !hasLocation(profile)) {
            missing.add(Field.LOCATION);
        }
        if (requireSpecialties && (profile == null || !hasSpecialties(profile))) {
            missing.add(Field.SPECIALTIES);
        }
        return missing;
    }

    public static void requireReady(User user, CreatorProfile profile, boolean requireSpecialties) {
        List<Field> missing = missingFields(user, profile, requireSpecialties);
        if (missing.isEmpty()) {
            return;
        }
        String fields = missing.stream()
                .map(Field::key)
                .collect(Collectors.joining(", "));
        throw new BusinessException(
                CODE_PROFILE_INCOMPLETE,
                "Complete your profile before continuing. Missing: " + fields + ".");
    }

    /**
     * True when the incoming service list adds at least one new service
     * (null id or id not present in the existing list).
     */
    public static boolean introducesNewServices(
            List<ProfileServiceDto> existing,
            List<ProfileServiceDto> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return false;
        }
        Set<UUID> existingIds = new HashSet<>();
        if (existing != null) {
            for (ProfileServiceDto item : existing) {
                if (item != null && item.id() != null) {
                    existingIds.add(item.id());
                }
            }
        }
        for (ProfileServiceDto item : incoming) {
            if (item == null) {
                continue;
            }
            if (item.id() == null || !existingIds.contains(item.id())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasContactValue(String legacy, List<ProfileContactEntryDto> entries) {
        if (hasText(legacy)) {
            return true;
        }
        if (entries == null) {
            return false;
        }
        for (ProfileContactEntryDto entry : entries) {
            if (entry != null && hasText(entry.value())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLink(CreatorProfile profile) {
        if (hasText(profile.getWebsiteUrl()) || hasText(profile.getCtaUrl())) {
            return true;
        }
        List<ProfileLinkDto> links = profile.getProfileLinks();
        if (links == null) {
            return false;
        }
        for (ProfileLinkDto link : links) {
            if (link != null && hasText(link.url())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLocation(CreatorProfile profile) {
        if (hasText(profile.getLocationCity()) || hasText(profile.getLocationCountry())) {
            return true;
        }
        return profile.getLocationLat() != null && profile.getLocationLng() != null;
    }

    private static boolean hasSpecialties(CreatorProfile profile) {
        List<String> specialties = profile.getSpecialties();
        if (specialties != null) {
            for (String item : specialties) {
                if (hasText(item)) {
                    return true;
                }
            }
        }
        return hasText(profile.getSpecialite());
    }

    /**
     * Only photos uploaded to app storage count as a profile photo.
     * External OAuth / letter avatars are rejected.
     */
    public static boolean isUploadedProfilePhoto(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return false;
        }
        String raw = avatarUrl.trim();
        if (raw.regionMatches(true, 0, "data:", 0, 5)) {
            return false;
        }
        String normalized = raw.toLowerCase(Locale.ROOT);
        return normalized.contains("/api/storage/profiles/public/")
                || normalized.startsWith("profiles/public/")
                || normalized.contains("/profiles/public/");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
