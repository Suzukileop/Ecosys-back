package com.plateforme.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateCreatorProfileDto(
        @Size(max = 8000)
        String bio,

        @Size(max = 150)
        String specialite,

        @Size(max = 500)
        String websiteUrl,

        String socialLinks,

        @Size(max = 255)
        String languages,

        @Size(max = 100)
        String ctaLabel,

        @Size(max = 500)
        String ctaUrl,

        @Size(max = 150)
        String locationCity,

        @Size(max = 100)
        String locationCountry,

        Double locationLat,

        Double locationLng,

        @Size(max = 80)
        String timezoneId,

        @Size(max = 300)
        String contactAddress,

        @Size(max = 50)
        String contactPhone,

        @Email
        @Size(max = 255)
        String contactEmail,

        @Valid
        @Size(max = 8)
        List<ProfileContactEntryDto> contactAddresses,

        @Valid
        @Size(max = 8)
        List<ProfileContactEntryDto> contactPhones,

        @Valid
        @Size(max = 8)
        List<ProfileContactEntryDto> contactEmails,

        @Size(max = 200)
        String availabilityHours,

        Boolean isAvailable,

        String contactVisibility,

        @Pattern(regexp = "BANNER|SPLIT|VIP_GOLD|VIP_AURORA|STAGE")
        String studioHeaderLayout,

        @Pattern(regexp = "DEFAULT|COMPACT|CENTERED|GRID")
        String studioHeaderContentStyle,

        @Pattern(regexp = "LEFT|CENTER|RIGHT")
        String studioTabNavAlign,

        @Size(max = 160)
        String studioContentHeadline,

        List<ProfileMediaBlock> whyMeBlocks,

        List<ProfileMediaBlock> experienceBlocks,

        @Min(0)
        @Max(80)
        Integer yearsOfExperience,

        List<ProfileStrengthToolDto> strengthsToolsMastered,

        @Size(max = 50)
        String gender,

        List<String> spokenLanguages,

        List<ProfileServiceDto> profileServices,

        List<FaqItemDto> faqItems,

        List<ProfileLinkDto> profileLinks,

        @Valid
        @Size(max = 12)
        List<ProfileTeamMemberDto> teamMembers,

        @Valid
        @Size(max = 24)
        List<ProfileGalleryItemDto> galleryItems,

        @Pattern(regexp = "WITHIN_1_HOUR|FEW_HOURS|WITHIN_DAY|WITHIN_2_3_DAYS|")
        @Size(max = 40)
        String typicalResponseTime
) {}
