package com.plateforme.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.dto.ContactVisibilitySettings;
import com.plateforme.user.dto.CreatorProfileDto;
import com.plateforme.user.dto.FaqItemDto;
import com.plateforme.user.dto.ProfileContactEntryDto;
import com.plateforme.user.dto.ProfileGalleryItemDto;
import com.plateforme.user.dto.ProfileLinkDto;
import com.plateforme.user.dto.ProfileMediaBlock;
import com.plateforme.user.dto.ProfileServiceDto;
import com.plateforme.user.dto.ProfileStrengthToolDto;
import com.plateforme.user.dto.ProfileTeamMemberDto;
import com.plateforme.user.dto.UpdateCreatorProfileDto;
import com.plateforme.user.entity.CreatorProfile;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.CreatorProfileRepository;
import com.plateforme.user.repository.CreatorProfileVisitRepository;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreatorProfileService {

    private final CreatorProfileRepository creatorProfileRepository;
    private final CreatorProfileVisitRepository creatorProfileVisitRepository;
    private final UserRepository userRepository;
    private final CreatorPortfolioService creatorPortfolioService;
    private final CreatorReviewService creatorReviewService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public CreatorProfileDto getMyProfile(UUID userId) {
        User user = requireCreatorUser(userId);
        CreatorProfile profile = getOrCreateProfile(user);
        long portfolioCount = creatorPortfolioService.countPublicCuratedPosts(userId);
        return toDto(profile, user, portfolioCount);
    }

    @Transactional
    public CreatorProfileDto updateMyProfile(UUID userId, UpdateCreatorProfileDto dto) {
        User user = requireCreatorUser(userId);
        CreatorProfile profile = getOrCreateProfile(user);

        if (dto.bio() != null) profile.setBio(ProfileBioSupport.normalize(dto.bio()));
        if (dto.specialties() != null || dto.specialite() != null) {
            List<String> source = dto.specialties() != null ? dto.specialties() : profile.getSpecialties();
            List<String> normalized = SpecialtyTaxonomy.normalizeSpecialties(source, dto.specialite());
            profile.setSpecialties(new ArrayList<>(normalized));
            profile.setSpecialite(SpecialtyTaxonomy.primaryOf(normalized));
            if (dto.strengthsToolsMastered() == null) {
                profile.setStrengthsToolsMastered(new ArrayList<>(
                        ProfileStoryFieldsSupport.normalizeStrengths(
                                profile.getStrengthsToolsMastered(),
                                userId,
                                normalized)));
            }
        }
        if (dto.specialtyTags() != null) {
            profile.setSpecialtyTags(new ArrayList<>(SpecialtyTaxonomy.normalizeTags(dto.specialtyTags())));
        }
        applyContactFields(profile, dto);
        if (dto.availabilityHours() != null) profile.setAvailabilityHours(dto.availabilityHours());
        if (dto.isAvailable() != null) profile.setIsAvailable(dto.isAvailable());
        if (dto.availabilityLabel() != null) {
            String label = dto.availabilityLabel().trim();
            profile.setAvailabilityLabel(label.isEmpty() ? null : label);
        }
        if (dto.contactVisibility() != null) profile.setContactVisibility(dto.contactVisibility());
        if (dto.studioHeaderLayout() != null) profile.setStudioHeaderLayout(dto.studioHeaderLayout());
        if (dto.studioHeaderContentStyle() != null) profile.setStudioHeaderContentStyle(dto.studioHeaderContentStyle());
        if (dto.studioTabNavAlign() != null) profile.setStudioTabNavAlign(dto.studioTabNavAlign());
        if (dto.studioContentHeadline() != null) {
            String headline = dto.studioContentHeadline().trim();
            profile.setStudioContentHeadline(headline.isEmpty() ? null : headline);
        }
        if (dto.shopName() != null) {
            String shopName = dto.shopName().trim();
            profile.setShopName(shopName.isEmpty() ? null : shopName);
        }
        if (dto.shopSellingFocus() != null) {
            String focus = dto.shopSellingFocus().trim();
            profile.setShopSellingFocus(focus.isEmpty() ? null : focus);
        }
        if (dto.shopDescription() != null) {
            String description = dto.shopDescription().trim();
            profile.setShopDescription(description.isEmpty() ? null : description);
        }
        if (dto.shopCoverUrl() != null) {
            String coverUrl = dto.shopCoverUrl().trim();
            profile.setShopCoverUrl(coverUrl.isEmpty() ? null : coverUrl);
        }

        if (dto.whyMeBlocks() != null) {
            profile.setWhyMeBlocks(new ArrayList<>(
                    ProfileStoryFieldsSupport.normalizeWhyMeBlocks(dto.whyMeBlocks(), userId)));
        }
        if (dto.experienceBlocks() != null) {
            profile.setExperienceBlocks(new ArrayList<>(
                    ProfileStoryFieldsSupport.normalizeBlocks(dto.experienceBlocks(), userId)));
            profile.setYearsOfExperience(
                    ProfileStoryFieldsSupport.normalizeYears(dto.yearsOfExperience()));
        }
        if (dto.strengthsToolsMastered() != null) {
            profile.setStrengthsToolsMastered(new ArrayList<>(
                    ProfileStoryFieldsSupport.normalizeStrengths(
                            dto.strengthsToolsMastered(),
                            userId,
                            profile.getSpecialties())));
        }

        if (dto.gender() != null) {
            profile.setGender(ProfileExtensionsSupport.normalizeGender(dto.gender()));
        }
        if (dto.nationality() != null) {
            profile.setNationality(ProfileExtensionsSupport.normalizeNationality(dto.nationality()));
        }
        if (dto.appRole() != null) {
            profile.setAppRole(ProfileExtensionsSupport.normalizeAppRole(dto.appRole()));
        }
        if (dto.spokenLanguages() != null || dto.languages() != null) {
            List<String> merged = ProfileLinksLegacySync.mergeSpokenLanguages(
                    dto.spokenLanguages(), dto.languages());
            profile.setSpokenLanguages(new ArrayList<>(
                    ProfileExtensionsSupport.normalizeSpokenLanguages(merged)));
        }
        if (dto.faqItems() != null) {
            profile.setFaqItems(new ArrayList<>(
                    ProfileExtensionsSupport.normalizeFaqItems(dto.faqItems())));
        }
        if (dto.teamMembers() != null) {
            profile.setTeamMembers(new ArrayList<>(
                    ProfileExtensionsSupport.normalizeTeamMembers(dto.teamMembers(), userId)));
        }
        if (dto.galleryItems() != null) {
            profile.setGalleryItems(new ArrayList<>(
                    ProfileExtensionsSupport.normalizeGalleryItems(dto.galleryItems(), userId)));
        }

        applyProfileLinks(profile, dto);
        applyLocation(profile, dto);

        if (dto.profileServices() != null) {
            if (CreatorProfileReadinessSupport.introducesNewServices(
                    profile.getProfileServices(), dto.profileServices())) {
                CreatorProfileReadinessSupport.requireReady(user, profile, true);
            }
            List<String> allowedSpecialties = SpecialtyTaxonomy.normalizeSpecialties(
                    profile.getSpecialties(), profile.getSpecialite());
            profile.setProfileServices(new ArrayList<>(
                    ProfileExtensionsSupport.normalizeServices(dto.profileServices(), allowedSpecialties)));
        }

        if (dto.typicalResponseTime() != null) {
            profile.setTypicalResponseTime(
                    CreatorResponseTimeService.normalizeTypicalResponseTime(dto.typicalResponseTime()));
        }

        ProfileLinksLegacySync.syncLegacyColumns(profile, objectMapper);

        profile = creatorProfileRepository.save(profile);
        log.debug("Profil créateur mis à jour pour user={}", userId);

        long portfolioCount = creatorPortfolioService.countPublicCuratedPosts(userId);
        return toDto(profile, user, portfolioCount);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPortfolioSettings(UUID userId) {
        User user = requireCreatorUser(userId);
        CreatorProfile profile = getOrCreateProfile(user);
        return PortfolioSettingsSupport.read(profile);
    }

    @Transactional
    public Map<String, Object> updatePortfolioSettings(UUID userId, Map<String, Object> settings) {
        User user = requireCreatorUser(userId);
        CreatorProfile profile = getOrCreateProfile(user);
        Map<String, Object> normalized = PortfolioSettingsSupport.normalize(settings, objectMapper);
        profile.setPortfolioSettings(new HashMap<>(normalized));
        creatorProfileRepository.save(profile);
        return PortfolioSettingsSupport.read(profile);
    }

    private void applyContactFields(CreatorProfile profile, UpdateCreatorProfileDto dto) {
        boolean useLists = dto.contactAddresses() != null
                || dto.contactPhones() != null
                || dto.contactEmails() != null;

        if (useLists) {
            if (dto.contactAddresses() != null) {
                List<ProfileContactEntryDto> addresses = ProfileExtensionsSupport.normalizeContactEntries(
                        dto.contactAddresses(),
                        ProfileExtensionsSupport.MAX_CONTACT_ENTRIES,
                        ProfileExtensionsSupport.MAX_CONTACT_ADDRESS,
                        "CONTACT_ADDRESSES");
                profile.setContactAddresses(new ArrayList<>(addresses));
                profile.setContactAddress(ProfileExtensionsSupport.firstContactValue(addresses));
            }
            if (dto.contactPhones() != null) {
                List<ProfileContactEntryDto> phones = ProfileExtensionsSupport.normalizeContactEntries(
                        dto.contactPhones(),
                        ProfileExtensionsSupport.MAX_CONTACT_ENTRIES,
                        ProfileExtensionsSupport.MAX_CONTACT_PHONE,
                        "CONTACT_PHONES");
                profile.setContactPhones(new ArrayList<>(phones));
                profile.setContactPhone(ProfileExtensionsSupport.firstContactValue(phones));
            }
            if (dto.contactEmails() != null) {
                List<ProfileContactEntryDto> emails = ProfileExtensionsSupport.normalizeContactEntries(
                        dto.contactEmails(),
                        ProfileExtensionsSupport.MAX_CONTACT_ENTRIES,
                        ProfileExtensionsSupport.MAX_CONTACT_EMAIL,
                        "CONTACT_EMAILS",
                        true);
                profile.setContactEmails(new ArrayList<>(emails));
                profile.setContactEmail(ProfileExtensionsSupport.firstContactValue(emails));
            }
            return;
        }

        if (dto.contactAddress() != null) {
            profile.setContactAddress(dto.contactAddress());
            profile.setContactAddresses(new ArrayList<>(
                    ProfileExtensionsSupport.contactEntriesFromLegacy(dto.contactAddress())));
        }
        if (dto.contactPhone() != null) {
            profile.setContactPhone(dto.contactPhone());
            profile.setContactPhones(new ArrayList<>(
                    ProfileExtensionsSupport.contactEntriesFromLegacy(dto.contactPhone())));
        }
        if (dto.contactEmail() != null) {
            profile.setContactEmail(dto.contactEmail());
            profile.setContactEmails(new ArrayList<>(
                    ProfileExtensionsSupport.contactEntriesFromLegacy(dto.contactEmail())));
        }
    }

    private void applyProfileLinks(CreatorProfile profile, UpdateCreatorProfileDto dto) {
        if (dto.profileLinks() != null) {
            profile.setProfileLinks(new ArrayList<>(
                    ProfileExtensionsSupport.normalizeLinks(dto.profileLinks())));
            return;
        }
        boolean legacyTouched = dto.websiteUrl() != null || dto.socialLinks() != null
                || dto.ctaLabel() != null || dto.ctaUrl() != null;
        if (!legacyTouched) {
            return;
        }
        List<ProfileLinkDto> merged = ProfileLinksLegacySync.mergeLinksFromLegacy(
                profile.getProfileLinks(),
                dto.websiteUrl() != null ? dto.websiteUrl() : profile.getWebsiteUrl(),
                dto.socialLinks() != null ? dto.socialLinks() : profile.getSocialLinks(),
                dto.ctaLabel() != null ? dto.ctaLabel() : profile.getCtaLabel(),
                dto.ctaUrl() != null ? dto.ctaUrl() : profile.getCtaUrl(),
                objectMapper);
        profile.setProfileLinks(new ArrayList<>(ProfileExtensionsSupport.normalizeLinks(merged)));
    }

    private void applyLocation(CreatorProfile profile, UpdateCreatorProfileDto dto) {
        boolean anyLocation = dto.locationCity() != null || dto.locationCountry() != null
                || dto.locationLat() != null || dto.locationLng() != null || dto.timezoneId() != null;
        if (!anyLocation) {
            return;
        }
        if (dto.locationLat() == null || dto.locationLng() == null || dto.timezoneId() == null
                || dto.timezoneId().isBlank()) {
            throw new BusinessException("LOCATION_REQUIRED",
                    "Enable device location to set city, coordinates and timezone.");
        }
        profile.setLocationLat(dto.locationLat());
        profile.setLocationLng(dto.locationLng());
        profile.setTimezoneId(dto.timezoneId().trim());
        profile.setLocationCity(dto.locationCity() != null ? dto.locationCity().trim() : null);
        profile.setLocationCountry(dto.locationCountry() != null ? dto.locationCountry().trim() : null);
    }

    private User requireCreatorUser(UUID userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND",
                        "Utilisateur introuvable : " + userId));
        boolean hasCreatorRole = user.getRoles().stream()
                .anyMatch(r -> "ROLE_CREATOR".equals(r.getName()));
        if (!hasCreatorRole) {
            throw new BusinessException("ROLE_REQUIRED",
                    "L'utilisateur doit avoir le rôle CREATOR pour gérer un profil créateur");
        }
        return user;
    }

    private CreatorProfile getOrCreateProfile(User user) {
        return creatorProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    CreatorProfile newProfile = new CreatorProfile();
                    newProfile.setUser(user);
                    newProfile.setContactVisibility(ContactVisibilitySettings.defaultJson());
                    log.info("Création d'un nouveau profil créateur pour user={}", user.getId());
                    return creatorProfileRepository.save(newProfile);
                });
    }

    private CreatorProfileDto toDto(CreatorProfile p, User user, long portfolioCount) {
        List<ProfileLinkDto> links = safeLinks(p.getProfileLinks());
        List<String> spoken = safeSpoken(p.getSpokenLanguages());
        String responseLabel = CreatorResponseTimeService.resolveResponseTimeLabel(
                p.getTypicalResponseTime(),
                p.getAvgResponseTimeSeconds(),
                p.getResponseTimeSampleCount());

        List<String> specialties = SpecialtyTaxonomy.normalizeSpecialties(
                p.getSpecialties(), p.getSpecialite());
        return new CreatorProfileDto(
                p.getId(),
                user.getId(),
                user.getFullName(),
                user.getAvatarUrl(),
                ProfileBioSupport.normalize(p.getBio()),
                SpecialtyTaxonomy.primaryOf(specialties) != null
                        ? SpecialtyTaxonomy.primaryOf(specialties)
                        : p.getSpecialite(),
                specialties,
                p.getSpecialtyTags() != null ? p.getSpecialtyTags() : List.of(),
                p.getWebsiteUrl(),
                p.getSocialLinks(),
                p.getIsVerified(),
                portfolioCount,
                p.getLanguages(),
                p.getCtaLabel(),
                p.getCtaUrl(),
                p.getLocationCity(),
                p.getLocationCountry(),
                p.getLocationLat(),
                p.getLocationLng(),
                p.getTimezoneId(),
                p.getContactAddress(),
                p.getContactPhone(),
                p.getContactEmail(),
                ProfileExtensionsSupport.contactEntriesForResponse(
                        p.getContactAddresses(), p.getContactAddress()),
                ProfileExtensionsSupport.contactEntriesForResponse(
                        p.getContactPhones(), p.getContactPhone()),
                ProfileExtensionsSupport.contactEntriesForResponse(
                        p.getContactEmails(), p.getContactEmail()),
                p.getAvailabilityHours(),
                Boolean.TRUE.equals(p.getIsAvailable()),
                p.getAvailabilityLabel(),
                p.getContactVisibility() != null
                        ? p.getContactVisibility()
                        : ContactVisibilitySettings.defaults().toJson(objectMapper),
                p.getStudioHeaderLayout() != null ? p.getStudioHeaderLayout() : "BANNER",
                p.getStudioHeaderContentStyle() != null ? p.getStudioHeaderContentStyle() : "DEFAULT",
                p.getStudioTabNavAlign() != null ? p.getStudioTabNavAlign() : "LEFT",
                p.getStudioContentHeadline(),
                p.getShopName(),
                p.getShopSellingFocus(),
                p.getShopDescription(),
                p.getShopCoverUrl(),
                ProfileStoryFieldsSupport.stripWhyMeMedia(safeBlocks(p.getWhyMeBlocks())),
                safeBlocks(p.getExperienceBlocks()),
                p.getYearsOfExperience(),
                safeStrengths(p.getStrengthsToolsMastered()),
                creatorReviewService.getReputation(user.getId(), 5),
                creatorProfileVisitRepository.countByCreatorUserId(user.getId()),
                p.getGender(),
                p.getNationality(),
                ProfileExtensionsSupport.normalizeAppRole(p.getAppRole()),
                spoken,
                safeServices(p.getProfileServices()),
                safeFaq(p.getFaqItems()),
                safeTeamMembers(p.getTeamMembers()),
                safeGalleryItems(p.getGalleryItems()),
                links,
                user.getCreatedAt(),
                responseLabel,
                p.getTypicalResponseTime(),
                p.getResponseTimeSampleCount()
        );
    }

    private static List<ProfileMediaBlock> safeBlocks(List<ProfileMediaBlock> blocks) {
        return blocks != null ? blocks : List.of();
    }

    private static List<ProfileStrengthToolDto> safeStrengths(List<ProfileStrengthToolDto> strengths) {
        return strengths != null ? strengths : List.of();
    }

    private static List<String> safeSpoken(List<String> spoken) {
        return spoken != null ? spoken : List.of();
    }

    private static List<ProfileServiceDto> safeServices(List<ProfileServiceDto> services) {
        return services != null ? services : List.of();
    }

    private static List<FaqItemDto> safeFaq(List<FaqItemDto> faq) {
        return faq != null ? faq : List.of();
    }

    private static List<ProfileTeamMemberDto> safeTeamMembers(List<ProfileTeamMemberDto> teamMembers) {
        return teamMembers != null ? teamMembers : List.of();
    }

    private static List<ProfileGalleryItemDto> safeGalleryItems(List<ProfileGalleryItemDto> galleryItems) {
        return galleryItems != null ? galleryItems : List.of();
    }

    private static List<ProfileLinkDto> safeLinks(List<ProfileLinkDto> links) {
        return links != null ? links : List.of();
    }
}
