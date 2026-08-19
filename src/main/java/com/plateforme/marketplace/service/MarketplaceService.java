package com.plateforme.marketplace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plateforme.marketplace.dto.ContentPostResponse;
import com.plateforme.marketplace.dto.CreatorProfileResponse;
import com.plateforme.marketplace.dto.SocialLink;
import com.plateforme.marketplace.repository.ContentPostRepository;
import com.plateforme.marketplace.repository.MarketplaceProductRepository;
import com.plateforme.marketplace.util.SocialLinksJsonParser;
import com.plateforme.ecosystem.storage.PublicMediaUrlResolver;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.dto.ContactVisibilityLevel;
import com.plateforme.user.dto.ContactVisibilitySettings;
import com.plateforme.user.dto.FaqItemDto;
import com.plateforme.user.dto.ProfileGalleryItemDto;
import com.plateforme.user.dto.ProfileLinkDto;
import com.plateforme.user.dto.ProfileMediaBlock;
import com.plateforme.user.dto.ProfileServiceDto;
import com.plateforme.user.dto.ProfileStrengthToolDto;
import com.plateforme.user.dto.ProfileTeamMemberDto;
import com.plateforme.user.entity.CreatorProfile;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.CreatorFollowRepository;
import com.plateforme.user.repository.CreatorProfileRepository;
import com.plateforme.user.repository.CreatorProfileVisitRepository;
import com.plateforme.user.service.CreatorFollowService;
import com.plateforme.user.service.CreatorPortfolioService;
import com.plateforme.user.service.CreatorSearchExpand;
import com.plateforme.user.service.PortfolioSettingsSupport;
import com.plateforme.user.service.CreatorResponseTimeService;
import com.plateforme.user.service.CreatorReviewService;
import com.plateforme.user.service.ProfileExtensionsSupport;
import com.plateforme.user.service.ProfileBioSupport;
import com.plateforme.user.service.SpecialtyTaxonomy;
import com.plateforme.user.service.ProfileStoryFieldsSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketplaceService {

    private record ResolvedPublicContact(
            String websiteUrl,
            List<SocialLink> socialLinks,
            String phone,
            String contactEmail,
            String availabilityHours,
            String contactAddress,
            String languages,
            String ctaLabel,
            String ctaUrl,
            String timezoneId,
            List<ProfileMediaBlock> whyMeBlocks,
            List<ProfileMediaBlock> experienceBlocks,
            Integer yearsOfExperience,
            List<ProfileStrengthToolDto> strengthsToolsMastered,
            String gender,
            List<String> spokenLanguages,
            List<ProfileServiceDto> profileServices,
            List<FaqItemDto> faqItems,
            List<ProfileLinkDto> profileLinks,
            String responseTimeLabel,
            Integer responseTimeSampleCount,
            boolean membersOnlyContactAvailable
    ) {}

    private final CreatorProfileRepository creatorProfileRepository;
    private final CreatorProfileVisitRepository creatorProfileVisitRepository;
    private final ContentPostRepository contentPostRepository;
    private final MarketplaceProductRepository productRepository;
    private final CreatorPortfolioService creatorPortfolioService;
    private final ObjectMapper objectMapper;
    private final CreatorReviewService creatorReviewService;
    private final CreatorFollowService creatorFollowService;
    private final CreatorFollowRepository creatorFollowRepository;
    private final PublicMediaUrlResolver publicMediaUrlResolver;

    @Transactional(readOnly = true)
    public Page<CreatorProfileResponse> getCreators(String specialite, Boolean verified, Boolean available,
                                                    String nationality, Integer minYearsExperience,
                                                    Double lat, Double lng, Double accuracyM, String sort,
                                                    UUID viewerUserId, Pageable pageable) {
        String specialitePrimary = resolveSpecialtyPrimary(specialite);
        String specialiteAlt = SpecialtyTaxonomy.alternateSearchTerm(specialite);
        String specialiteSignals = specialitePrimary == null
                ? ""
                : CreatorSearchExpand.specialtySignalsPipe(specialitePrimary);
        String nationalityFilter = ProfileExtensionsSupport.normalizeNationality(nationality);
        Integer yearsFilter = normalizeMinYearsExperience(minYearsExperience);
        boolean byDistance = wantsDistanceSort(sort, lat, lng);
        boolean withDistance = canPublishDistance(lat, lng, accuracyM);

        Page<CreatorProfile> page = byDistance
                ? creatorProfileRepository.findForMarketplaceByDistance(
                        specialitePrimary, specialiteAlt, specialiteSignals, verified, available,
                        nationalityFilter, yearsFilter, lat, lng, pageable)
                : creatorProfileRepository.findForMarketplace(
                        specialitePrimary, specialiteAlt, specialiteSignals, verified, available,
                        nationalityFilter, yearsFilter, pageable);
        List<UUID> creatorIds = page.getContent().stream().map(p -> p.getUser().getId()).toList();
        Set<UUID> followedIds = creatorFollowService.getFollowedCreatorIds(viewerUserId, creatorIds);
        Map<UUID, Long> followerCounts = creatorFollowService.getFollowerCounts(creatorIds);

        return page.map(profile -> toCreatorCard(
                profile,
                viewerUserId != null,
                followerCounts.getOrDefault(profile.getUser().getId(), 0L),
                followedIds.contains(profile.getUser().getId()),
                withDistance ? publishableDistanceKm(lat, lng, accuracyM, profile) : null));
    }

    @Transactional(readOnly = true)
    public Page<CreatorProfileResponse> searchCreators(String keyword, Boolean verified, Boolean available,
                                                       String nationality, String specialite,
                                                       Integer minYearsExperience, Double lat, Double lng,
                                                       Double accuracyM, String sort, UUID viewerUserId,
                                                       Pageable pageable) {
        String nationalityFilter = ProfileExtensionsSupport.normalizeNationality(nationality);
        String specialitePrimary = resolveSpecialtyPrimary(specialite);
        String specialiteAlt = SpecialtyTaxonomy.alternateSearchTerm(specialite);
        String specialiteSignals = specialitePrimary == null
                ? ""
                : CreatorSearchExpand.specialtySignalsPipe(specialitePrimary);
        Integer yearsFilter = normalizeMinYearsExperience(minYearsExperience);
        boolean byDistance = wantsDistanceSort(sort, lat, lng);
        boolean withDistance = canPublishDistance(lat, lng, accuracyM);
        Page<CreatorProfile> page;
        if (keyword == null || keyword.isBlank()) {
            page = byDistance
                    ? creatorProfileRepository.findForMarketplaceByDistance(
                            specialitePrimary, specialiteAlt, specialiteSignals, verified, available,
                            nationalityFilter, yearsFilter, lat, lng, pageable)
                    : creatorProfileRepository.findForMarketplace(
                            specialitePrimary, specialiteAlt, specialiteSignals, verified, available,
                            nationalityFilter, yearsFilter, pageable);
        } else {
            String q = keyword.trim();
            if (q.length() > SpecialtyTaxonomy.MAX_SPECIALTY_LENGTH) {
                q = q.substring(0, SpecialtyTaxonomy.MAX_SPECIALTY_LENGTH);
            }
            String qCanonical = SpecialtyTaxonomy.canonicalize(q);
            if (qCanonical == null || qCanonical.equalsIgnoreCase(q)) {
                qCanonical = "";
            }
            String qExpanded = CreatorSearchExpand.expandedTermsPipe(q);
            if (byDistance) {
                page = creatorProfileRepository.searchByBioOrSpecialiteByDistance(
                        q, qCanonical, qExpanded, verified, available, nationalityFilter,
                        specialitePrimary, specialiteAlt, specialiteSignals, yearsFilter, lat, lng, pageable);
            } else {
                page = creatorProfileRepository.searchByBioOrSpecialite(
                        q, qCanonical, qExpanded, verified, available, nationalityFilter,
                        specialitePrimary, specialiteAlt, specialiteSignals, yearsFilter, pageable);
            }
        }

        List<UUID> creatorIds = page.getContent().stream().map(p -> p.getUser().getId()).toList();
        Set<UUID> followedIds = creatorFollowService.getFollowedCreatorIds(viewerUserId, creatorIds);
        Map<UUID, Long> followerCounts = creatorFollowService.getFollowerCounts(creatorIds);

        return page.map(profile -> toCreatorCard(
                profile,
                viewerUserId != null,
                followerCounts.getOrDefault(profile.getUser().getId(), 0L),
                followedIds.contains(profile.getUser().getId()),
                withDistance ? publishableDistanceKm(lat, lng, accuracyM, profile) : null));
    }

    @Transactional(readOnly = true)
    public List<String> suggestSpecialties(String q) {
        String query = q == null ? "" : q.trim();
        if (query.length() > SpecialtyTaxonomy.MAX_SPECIALTY_LENGTH) {
            query = query.substring(0, SpecialtyTaxonomy.MAX_SPECIALTY_LENGTH);
        }
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        if (!query.isBlank()) {
            String likeQuery = escapeLike(query);
            for (String label : creatorProfileRepository.suggestSpecialties(likeQuery)) {
                String sanitized = SpecialtyTaxonomy.sanitizeLabel(label);
                if (sanitized != null) {
                    String canonical = SpecialtyTaxonomy.canonicalize(sanitized);
                    String resolved = canonical != null ? canonical : sanitized;
                    unique.putIfAbsent(resolved.toLowerCase(Locale.ROOT), resolved);
                }
            }
            String fromQuery = SpecialtyTaxonomy.canonicalize(query);
            if (fromQuery != null) {
                unique.putIfAbsent(fromQuery.toLowerCase(Locale.ROOT), fromQuery);
            }
            String needle = query.toLowerCase(Locale.ROOT);
            String compactNeedle = needle.replaceAll("[^a-z0-9]+", "");
            for (String label : SpecialtyTaxonomy.LABELS) {
                String lower = label.toLowerCase(Locale.ROOT);
                String compact = lower.replaceAll("[^a-z0-9]+", "");
                if (lower.contains(needle) || (!compactNeedle.isEmpty() && compact.contains(compactNeedle))) {
                    unique.putIfAbsent(lower, label);
                }
            }
        }
        List<String> values = new ArrayList<>(unique.values());
        String rankQuery = query.toLowerCase(Locale.ROOT);
        values.sort(Comparator
                .comparingInt((String label) -> specialtySuggestionRank(label, rankQuery))
                .thenComparing(label -> label.toLowerCase(Locale.ROOT)));
        if (values.size() > 20) {
            return List.copyOf(values.subList(0, 20));
        }
        return List.copyOf(values);
    }

    @Transactional(readOnly = true)
    public CreatorProfileResponse getCreatorPublicProfile(UUID userId, UUID viewerUserId) {
        CreatorProfile profile = creatorProfileRepository.findByUserIdAndUserDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("CREATOR_NOT_FOUND",
                        "Créateur introuvable : " + userId));

        User user = profile.getUser();
        long portfolioCount = creatorPortfolioService.countPublicCuratedPosts(userId);
        long contentCount = contentPostRepository.countByCreator_Id(userId);
        long productCount = productRepository.countByCreator_IdAndIsPublishedTrue(userId);

        List<ContentPostResponse> portfolioPosts = creatorPortfolioService.getPublicCuratedPosts(userId);

        boolean authenticated = viewerUserId != null;
        ResolvedPublicContact contact = resolvePublicContact(profile, user, authenticated);

        Double averageRating = creatorReviewService.getReputation(userId, 0).averageRating();
        long followerCount = creatorFollowService.getFollowerCount(userId);
        boolean isFollowing = creatorFollowService.isFollowing(viewerUserId, userId);

        return buildResponse(
                profile,
                user,
                contact,
                portfolioCount,
                contentCount,
                productCount,
                averageRating,
                followerCount,
                isFollowing,
                portfolioPosts,
                null);
    }

    @Transactional(readOnly = true)
    public Page<CreatorProfileResponse> getFollowingCreators(UUID followerId, Pageable pageable) {
        return creatorFollowRepository.findByFollower_IdOrderByCreatedAtDesc(followerId, pageable)
                .map(follow -> {
                    UUID creatorId = follow.getCreator().getId();
                    return getCreatorPublicProfile(creatorId, followerId);
                });
    }

    private CreatorProfileResponse toCreatorCard(
            CreatorProfile profile,
            boolean authenticated,
            long followerCount,
            boolean isFollowing,
            Double distanceKm) {
        User user = profile.getUser();
        UUID userId = user.getId();
        long portfolioCount = creatorPortfolioService.countPublicCuratedPosts(userId);
        long contentCount = contentPostRepository.countByCreator_Id(userId);
        long productCount = productRepository.countByCreator_IdAndIsPublishedTrue(userId);

        ResolvedPublicContact contact = resolvePublicContact(profile, user, authenticated);
        Double averageRating = creatorReviewService.getReputation(userId, 0).averageRating();

        return buildResponse(
                profile,
                user,
                contact,
                portfolioCount,
                contentCount,
                productCount,
                averageRating,
                followerCount,
                isFollowing,
                List.of(),
                distanceKm);
    }

    private CreatorProfileResponse buildResponse(
            CreatorProfile profile,
            User user,
            ResolvedPublicContact contact,
            long portfolioCount,
            long contentCount,
            long productCount,
            Double averageRating,
            long followerCount,
            boolean isFollowing,
            List<ContentPostResponse> portfolioPosts,
            Double distanceKm) {
        long serviceCount = ProfileExtensionsSupport.countActiveServices(profile.getProfileServices());
        List<String> specialties = SpecialtyTaxonomy.normalizeSpecialties(
                profile.getSpecialties(), profile.getSpecialite());
        String bio = ProfileBioSupport.normalize(profile.getBio());
        return new CreatorProfileResponse(
                user.getId(),
                user.getFullName(),
                publicMediaUrlResolver.resolveAvatarUrl(user.getAvatarUrl()),
                contact.phone(),
                bio,
                SpecialtyTaxonomy.primaryOf(specialties) != null
                        ? SpecialtyTaxonomy.primaryOf(specialties)
                        : profile.getSpecialite(),
                specialties,
                profile.getSpecialtyTags() != null ? List.copyOf(profile.getSpecialtyTags()) : List.of(),
                contact.websiteUrl(),
                contact.socialLinks(),
                Boolean.TRUE.equals(profile.getIsVerified()),
                Boolean.TRUE.equals(profile.getIsAvailable()),
                profile.getAvailabilityLabel(),
                portfolioCount,
                contentCount,
                productCount,
                serviceCount,
                averageRating,
                profile.getStudioHeaderLayout() != null ? profile.getStudioHeaderLayout() : "BANNER",
                profile.getStudioHeaderContentStyle() != null ? profile.getStudioHeaderContentStyle() : "DEFAULT",
                profile.getStudioTabNavAlign() != null ? profile.getStudioTabNavAlign() : "LEFT",
                profile.getLocationCity(),
                profile.getLocationCountry(),
                profile.getLocationLat(),
                profile.getLocationLng(),
                profile.getNationality(),
                profile.getAppRole(),
                portfolioPosts.isEmpty() ? portfolioPosts : List.copyOf(portfolioPosts),
                followerCount,
                isFollowing,
                contact.contactEmail(),
                contact.availabilityHours(),
                contact.contactAddress(),
                contact.membersOnlyContactAvailable(),
                contact.languages(),
                contact.ctaLabel(),
                contact.ctaUrl(),
                contact.timezoneId(),
                contact.whyMeBlocks(),
                contact.experienceBlocks(),
                contact.yearsOfExperience(),
                contact.strengthsToolsMastered(),
                creatorProfileVisitRepository.countByCreatorUserId(profile.getUser().getId()),
                contact.gender(),
                contact.spokenLanguages(),
                contact.profileServices(),
                contact.faqItems(),
                safeTeamMembers(profile.getTeamMembers()),
                safeGalleryItems(profile.getGalleryItems()),
                profile.getAboutUs(),
                contact.profileLinks(),
                user.getCreatedAt(),
                contact.responseTimeLabel(),
                contact.responseTimeSampleCount(),
                portfolioPosts.isEmpty() ? List.of() : List.copyOf(portfolioPosts),
                PortfolioSettingsSupport.read(profile),
                blankToNull(profile.getShopName()),
                blankToNull(profile.getShopSellingFocus()),
                blankToNull(profile.getShopDescription()),
                blankToNull(profile.getShopCoverUrl()),
                distanceKm
        );
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String trimSpecialtyFilter(String specialite) {
        if (specialite == null || specialite.isBlank()) {
            return null;
        }
        return specialite.trim();
    }

    /** Prefer canonical Popular label for exact specialty matching; null when blank. */
    private static String resolveSpecialtyPrimary(String specialite) {
        String trimmed = trimSpecialtyFilter(specialite);
        if (trimmed == null) {
            return null;
        }
        String primary = SpecialtyTaxonomy.primarySearchTerm(trimmed);
        return primary != null ? primary : trimmed;
    }

    private static Integer normalizeMinYearsExperience(Integer minYearsExperience) {
        if (minYearsExperience == null || minYearsExperience < 1) {
            return null;
        }
        return Math.min(minYearsExperience, 80);
    }

    private static int specialtySuggestionRank(String label, String query) {
        if (query == null || query.isBlank()) {
            return 2;
        }
        String lower = label.toLowerCase(Locale.ROOT);
        if (lower.equals(query)) {
            return 0;
        }
        if (lower.startsWith(query)) {
            return 1;
        }
        return 2;
    }

    private static boolean hasViewerLocation(Double lat, Double lng) {
        if (lat == null || lng == null) {
            return false;
        }
        return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    /**
     * GPS / Wi-Fi / IP fixes often report 2–50 km accuracy. Publishing that as
     * "Less than 1 km away" is misleading — only emit distance when the error
     * bar is small enough to trust the displayed bucket.
     */
    static final double DISTANCE_MAX_ACCURACY_M = 500.0;

    private static boolean canPublishDistance(Double lat, Double lng, Double accuracyM) {
        if (!hasViewerLocation(lat, lng)) {
            return false;
        }
        if (accuracyM == null || !Double.isFinite(accuracyM) || accuracyM <= 0) {
            return false;
        }
        return accuracyM <= DISTANCE_MAX_ACCURACY_M;
    }

    private static boolean wantsDistanceSort(String sort, Double lat, Double lng) {
        if (!hasViewerLocation(lat, lng)) {
            return false;
        }
        return sort != null && sort.equalsIgnoreCase("distance");
    }

    private static Double publishableDistanceKm(
            Double viewerLat, Double viewerLng, Double accuracyM, CreatorProfile profile) {
        if (!canPublishDistance(viewerLat, viewerLng, accuracyM)) {
            return null;
        }
        Double km = distanceKm(viewerLat, viewerLng, profile);
        if (km == null) {
            return null;
        }
        double accuracyKm = accuracyM / 1000.0;
        // Hide when the GPS error could flip the shown label (especially "< 1 km").
        if (km < 1.0 && accuracyKm > 0.35) {
            return null;
        }
        if (km >= 1.0 && accuracyKm > km) {
            return null;
        }
        return km;
    }

    private static Double distanceKm(Double viewerLat, Double viewerLng, CreatorProfile profile) {
        if (viewerLat == null || viewerLng == null) {
            return null;
        }
        Double lat = profile.getLocationLat();
        Double lng = profile.getLocationLng();
        if (lat == null || lng == null) {
            return null;
        }
        if (!Double.isFinite(lat) || !Double.isFinite(lng)
                || (lat == 0.0 && lng == 0.0)) {
            return null;
        }
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat - viewerLat);
        double dLng = Math.toRadians(lng - viewerLng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(viewerLat)) * Math.cos(Math.toRadians(lat))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(earthRadiusKm * c * 10.0) / 10.0;
    }

    private static List<ProfileTeamMemberDto> safeTeamMembers(List<ProfileTeamMemberDto> teamMembers) {
        return teamMembers != null ? teamMembers : List.of();
    }

    private static List<ProfileGalleryItemDto> safeGalleryItems(List<ProfileGalleryItemDto> galleryItems) {
        return galleryItems != null ? galleryItems : List.of();
    }

    private ResolvedPublicContact resolvePublicContact(CreatorProfile profile, User user, boolean authenticated) {
        ContactVisibilitySettings visibility = ContactVisibilitySettings.fromJson(
                objectMapper, profile.getContactVisibility());

        List<ProfileLinkDto> allLinks = profile.getProfileLinks() != null ? profile.getProfileLinks() : List.of();
        List<SocialLink> allSocial = extractSocialLinks(allLinks, profile.getSocialLinks());
        String website = firstNonBlank(extractWebsite(allLinks), blankToNull(profile.getWebsiteUrl()));
        String phone = firstNonBlank(
                ProfileExtensionsSupport.firstContactValue(profile.getContactPhones()),
                firstNonBlank(profile.getContactPhone(), user.getPhone()));
        String email = firstNonBlank(
                ProfileExtensionsSupport.firstContactValue(profile.getContactEmails()),
                blankToNull(profile.getContactEmail()));
        String availability = blankToNull(profile.getAvailabilityHours());
        List<String> spoken = profile.getSpokenLanguages() != null ? profile.getSpokenLanguages() : List.of();
        String languagesLegacy = !spoken.isEmpty()
                ? String.join(", ", spoken)
                : blankToNull(profile.getLanguages());
        String ctaLabel = firstNonBlank(extractCtaLabel(allLinks), blankToNull(profile.getCtaLabel()));
        String ctaUrl = firstNonBlank(extractCtaUrl(allLinks), blankToNull(profile.getCtaUrl()));
        boolean hasCta = ctaLabel != null || ctaUrl != null;
        List<ProfileMediaBlock> whyMeBlocks = ProfileStoryFieldsSupport.stripWhyMeMedia(
                profile.getWhyMeBlocks() != null ? profile.getWhyMeBlocks() : List.of());
        List<ProfileMediaBlock> experienceBlocks = profile.getExperienceBlocks() != null
                ? profile.getExperienceBlocks() : List.of();
        Integer years = profile.getYearsOfExperience();
        List<ProfileStrengthToolDto> strengths = profile.getStrengthsToolsMastered() != null
                ? profile.getStrengthsToolsMastered() : List.of();
        List<ProfileServiceDto> services = ProfileExtensionsSupport.activeServices(
                profile.getProfileServices() != null ? profile.getProfileServices() : List.of());
        List<FaqItemDto> faq = profile.getFaqItems() != null ? profile.getFaqItems() : List.of();
        String gender = ProfileExtensionsSupport.normalizeGender(profile.getGender());
        String responseLabel = CreatorResponseTimeService.resolveResponseTimeLabel(
                profile.getTypicalResponseTime(),
                profile.getAvgResponseTimeSeconds(),
                profile.getResponseTimeSampleCount());
        Integer responseSampleCount = profile.getResponseTimeSampleCount();

        boolean hasWhyMe = !whyMeBlocks.isEmpty();
        boolean hasExperience = !experienceBlocks.isEmpty();
        boolean hasYears = years != null;
        boolean hasStrengths = strengths.stream()
                .anyMatch(s -> s != null && s.name() != null && !s.name().isBlank());
        boolean hasServices = !services.isEmpty();
        boolean hasFaq = !faq.isEmpty();
        boolean hasLinks = !allLinks.isEmpty();
        boolean hasSpoken = !spoken.isEmpty();
        boolean hasResponseTime = responseLabel != null;

        boolean membersOnlyContactAvailable = !authenticated && (
                hasMembersOnlyValue(visibility.email(), email)
                        || hasMembersOnlyValue(visibility.phone(), phone)
                        || hasMembersOnlyValue(visibility.availability(), availability)
                        || hasMembersOnlyValue(visibility.website(), website)
                        || hasMembersOnlyValue(visibility.languages(), languagesLegacy)
                        || (visibility.cta() == ContactVisibilityLevel.MEMBERS && hasCta)
                        || (visibility.social() == ContactVisibilityLevel.MEMBERS && !allSocial.isEmpty())
                        || (visibility.whyMe() == ContactVisibilityLevel.MEMBERS && hasWhyMe)
                        || (visibility.experience() == ContactVisibilityLevel.MEMBERS && hasExperience)
                        || (visibility.yearsOfExperience() == ContactVisibilityLevel.MEMBERS && hasYears)
                        || (visibility.strengthsTools() == ContactVisibilityLevel.MEMBERS && hasStrengths)
                        || (visibility.services() == ContactVisibilityLevel.MEMBERS && hasServices)
                        || (visibility.faq() == ContactVisibilityLevel.MEMBERS && hasFaq)
                        || (visibility.links() == ContactVisibilityLevel.MEMBERS && hasLinks)
                        || (visibility.gender() == ContactVisibilityLevel.MEMBERS && gender != null)
                        || (visibility.spokenLanguages() == ContactVisibilityLevel.MEMBERS && hasSpoken)
                        || (visibility.responseTime() == ContactVisibilityLevel.MEMBERS && hasResponseTime)
        );

        boolean showAvailability = visibility.availability().visibleTo(authenticated) && availability != null;
        boolean showLinks = visibility.links().visibleTo(authenticated);
        boolean showSocial = visibility.social().visibleTo(authenticated);
        boolean showWebsite = visibility.website().visibleTo(authenticated);
        boolean showCta = visibility.cta().visibleTo(authenticated);

        List<ProfileLinkDto> visibleLinks = showLinks ? allLinks : List.of();
        List<SocialLink> visibleSocial = showSocial ? allSocial : List.of();

        return new ResolvedPublicContact(
                showWebsite ? website : null,
                visibleSocial,
                visibility.phone().visibleTo(authenticated) ? phone : null,
                visibility.email().visibleTo(authenticated) ? email : null,
                showAvailability ? availability : null,
                /* Street address stays private on marketplace public profiles (city/zone only). */
                null,
                visibility.languages().visibleTo(authenticated) ? languagesLegacy : null,
                showCta ? ctaLabel : null,
                showCta ? ctaUrl : null,
                showAvailability ? blankToNull(profile.getTimezoneId()) : null,
                visibility.whyMe().visibleTo(authenticated) && hasWhyMe ? whyMeBlocks : List.of(),
                visibility.experience().visibleTo(authenticated) && hasExperience ? experienceBlocks : List.of(),
                visibility.yearsOfExperience().visibleTo(authenticated) && hasYears ? years : null,
                visibility.strengthsTools().visibleTo(authenticated) && hasStrengths ? strengths : List.of(),
                visibility.gender().visibleTo(authenticated) ? gender : null,
                visibility.spokenLanguages().visibleTo(authenticated) && hasSpoken ? spoken : List.of(),
                visibility.services().visibleTo(authenticated) && hasServices ? services : List.of(),
                visibility.faq().visibleTo(authenticated) && hasFaq ? faq : List.of(),
                visibleLinks,
                visibility.responseTime().visibleTo(authenticated) ? responseLabel : null,
                visibility.responseTime().visibleTo(authenticated) ? responseSampleCount : null,
                membersOnlyContactAvailable
        );
    }

    private List<SocialLink> extractSocialLinks(List<ProfileLinkDto> links, String legacySocialJson) {
        List<SocialLink> fromLinks = links.stream()
                .filter(link -> link != null && "SOCIAL".equalsIgnoreCase(link.type()))
                .filter(link -> link.url() != null && !link.url().isBlank())
                .map(link -> new SocialLink(
                        link.platform() != null ? link.platform() : link.label(),
                        link.url().trim()))
                .toList();
        if (!fromLinks.isEmpty()) {
            return fromLinks;
        }
        return SocialLinksJsonParser.parse(objectMapper, legacySocialJson);
    }

    private static String extractWebsite(List<ProfileLinkDto> links) {
        return links.stream()
                .filter(link -> link != null && "WEBSITE".equalsIgnoreCase(link.type()))
                .map(ProfileLinkDto::url)
                .filter(url -> url != null && !url.isBlank())
                .map(String::trim)
                .findFirst()
                .orElse(null);
    }

    private static String extractCtaLabel(List<ProfileLinkDto> links) {
        return links.stream()
                .filter(link -> link != null && "CTA".equalsIgnoreCase(link.type()))
                .map(ProfileLinkDto::label)
                .filter(label -> label != null && !label.isBlank())
                .map(String::trim)
                .findFirst()
                .orElse(null);
    }

    private static String extractCtaUrl(List<ProfileLinkDto> links) {
        return links.stream()
                .filter(link -> link != null && "CTA".equalsIgnoreCase(link.type()))
                .map(ProfileLinkDto::url)
                .filter(url -> url != null && !url.isBlank())
                .map(String::trim)
                .findFirst()
                .orElse(null);
    }

    private static boolean hasMembersOnlyValue(ContactVisibilityLevel level, String value) {
        return level == ContactVisibilityLevel.MEMBERS && value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }
}
