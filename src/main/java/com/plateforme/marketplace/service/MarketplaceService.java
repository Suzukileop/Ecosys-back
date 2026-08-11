package com.plateforme.marketplace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plateforme.marketplace.dto.ContentPostResponse;
import com.plateforme.marketplace.dto.CreatorProfileResponse;
import com.plateforme.marketplace.dto.SocialLink;
import com.plateforme.marketplace.repository.ContentPostRepository;
import com.plateforme.marketplace.repository.MarketplaceProductRepository;
import com.plateforme.marketplace.util.SocialLinksJsonParser;
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
import com.plateforme.user.service.CreatorFollowService;
import com.plateforme.user.service.CreatorPortfolioService;
import com.plateforme.user.service.PortfolioSettingsSupport;
import com.plateforme.user.service.CreatorResponseTimeService;
import com.plateforme.user.service.CreatorReviewService;
import com.plateforme.user.service.ProfileExtensionsSupport;
import com.plateforme.user.service.ProfileStoryFieldsSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    private final ContentPostRepository contentPostRepository;
    private final MarketplaceProductRepository productRepository;
    private final CreatorPortfolioService creatorPortfolioService;
    private final ObjectMapper objectMapper;
    private final CreatorReviewService creatorReviewService;
    private final CreatorFollowService creatorFollowService;
    private final CreatorFollowRepository creatorFollowRepository;

    @Transactional(readOnly = true)
    public Page<CreatorProfileResponse> getCreators(String specialite, Boolean verified, Boolean available,
                                                    UUID viewerUserId, Pageable pageable) {
        String specialiteFilter = specialite != null && !specialite.isBlank() ? specialite.trim() : null;

        Page<CreatorProfile> page = creatorProfileRepository.findForMarketplace(specialiteFilter, verified, available, pageable);
        List<UUID> creatorIds = page.getContent().stream().map(p -> p.getUser().getId()).toList();
        Set<UUID> followedIds = creatorFollowService.getFollowedCreatorIds(viewerUserId, creatorIds);
        Map<UUID, Long> followerCounts = creatorFollowService.getFollowerCounts(creatorIds);

        return page.map(profile -> toCreatorCard(
                profile,
                viewerUserId != null,
                followerCounts.getOrDefault(profile.getUser().getId(), 0L),
                followedIds.contains(profile.getUser().getId())));
    }

    @Transactional(readOnly = true)
    public Page<CreatorProfileResponse> searchCreators(String keyword, Boolean available, UUID viewerUserId,
                                                       Pageable pageable) {
        Page<CreatorProfile> page;
        if (keyword == null || keyword.isBlank()) {
            page = creatorProfileRepository.findForMarketplace(null, null, available, pageable);
        } else {
            page = creatorProfileRepository.searchByBioOrSpecialite(keyword.trim(), available, pageable);
        }

        List<UUID> creatorIds = page.getContent().stream().map(p -> p.getUser().getId()).toList();
        Set<UUID> followedIds = creatorFollowService.getFollowedCreatorIds(viewerUserId, creatorIds);
        Map<UUID, Long> followerCounts = creatorFollowService.getFollowerCounts(creatorIds);

        return page.map(profile -> toCreatorCard(
                profile,
                viewerUserId != null,
                followerCounts.getOrDefault(profile.getUser().getId(), 0L),
                followedIds.contains(profile.getUser().getId())));
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
                portfolioPosts);
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
            boolean isFollowing) {
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
                List.of());
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
            List<ContentPostResponse> portfolioPosts) {
        return new CreatorProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getAvatarUrl(),
                profile.getCoverUrl(),
                profile.getCoverObjectPositionY() != null ? profile.getCoverObjectPositionY() : 50,
                contact.phone(),
                profile.getBio(),
                profile.getSpecialite(),
                contact.websiteUrl(),
                contact.socialLinks(),
                Boolean.TRUE.equals(profile.getIsVerified()),
                Boolean.TRUE.equals(profile.getIsAvailable()),
                portfolioCount,
                contentCount,
                productCount,
                averageRating,
                profile.getStudioHeaderLayout() != null ? profile.getStudioHeaderLayout() : "BANNER",
                profile.getStudioHeaderContentStyle() != null ? profile.getStudioHeaderContentStyle() : "DEFAULT",
                profile.getStudioTabNavAlign() != null ? profile.getStudioTabNavAlign() : "LEFT",
                profile.getLocationCity(),
                profile.getLocationCountry(),
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
                profile.getProfileVisits() != null ? profile.getProfileVisits().longValue() : 0L,
                contact.gender(),
                contact.spokenLanguages(),
                contact.profileServices(),
                contact.faqItems(),
                safeTeamMembers(profile.getTeamMembers()),
                safeGalleryItems(profile.getGalleryItems()),
                contact.profileLinks(),
                user.getCreatedAt(),
                contact.responseTimeLabel(),
                contact.responseTimeSampleCount(),
                portfolioPosts.isEmpty() ? List.of() : List.copyOf(portfolioPosts),
                PortfolioSettingsSupport.read(profile)
        );
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
        String address = firstNonBlank(
                ProfileExtensionsSupport.firstContactValue(profile.getContactAddresses()),
                blankToNull(profile.getContactAddress()));
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
        List<ProfileServiceDto> services = profile.getProfileServices() != null
                ? profile.getProfileServices() : List.of();
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
                        || hasMembersOnlyValue(visibility.address(), address)
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
                visibility.address().visibleTo(authenticated) ? address : null,
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
