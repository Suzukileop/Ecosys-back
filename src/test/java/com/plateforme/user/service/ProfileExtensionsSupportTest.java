package com.plateforme.user.service;

import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.dto.FaqItemDto;
import com.plateforme.user.dto.ProfileAboutUsDto;
import com.plateforme.user.dto.ProfileAboutUsFounderDto;
import com.plateforme.user.dto.ProfileEducationEntryDto;
import com.plateforme.user.dto.ProfileSkillEntryDto;
import com.plateforme.user.dto.ProfileContactEntryDto;
import com.plateforme.user.dto.ProfileGalleryItemDto;
import com.plateforme.user.dto.ProfileLinkDto;
import com.plateforme.user.dto.ProfilePortfolioWorkDto;
import com.plateforme.user.dto.ProfileServiceDto;
import com.plateforme.user.dto.ProfileSpokenLanguageDto;
import com.plateforme.user.dto.ProfileTeamMemberDto;
import com.plateforme.user.dto.ProfileTeamSocialLinkDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfileExtensionsSupportTest {

    @Test
    @DisplayName("normalizeFaqItems enforces max 5 items")
    void normalizeFaqItems_maxFive() {
        List<FaqItemDto> items = List.of(
                faq(0), faq(1), faq(2), faq(3), faq(4), faq(5)
        );
        assertThrows(BusinessException.class, () -> ProfileExtensionsSupport.normalizeFaqItems(items));
    }

    @Test
    @DisplayName("normalizeServices enforces max 8 items")
    void normalizeServices_maxEight() {
        List<ProfileServiceDto> items = List.of(
                service(0), service(1), service(2), service(3),
                service(4), service(5), service(6), service(7), service(8)
        );
        assertThrows(BusinessException.class, () -> ProfileExtensionsSupport.normalizeServices(items));
    }

    @Test
    @DisplayName("normalizeLinks enforces max 10 items and rejects unsafe URLs")
    void normalizeLinks_limitsAndValidates() {
        List<ProfileLinkDto> tooMany = List.of(
                link(0), link(1), link(2), link(3), link(4),
                link(5), link(6), link(7), link(8), link(9), link(10)
        );
        assertThrows(BusinessException.class, () -> ProfileExtensionsSupport.normalizeLinks(tooMany));

        assertThrows(BusinessException.class, () -> ProfileExtensionsSupport.normalizeLinks(List.of(
                new ProfileLinkDto(UUID.randomUUID(), "WEBSITE", "Site", "javascript:alert(1)", 0, null, null)
        )));
    }

    @Test
    @DisplayName("normalizeSpokenLanguages deduplicates case and accent variants")
    void normalizeSpokenLanguages_dedupes() {
        List<ProfileSpokenLanguageDto> result = ProfileExtensionsSupport.normalizeSpokenLanguages(
                List.of(
                        new ProfileSpokenLanguageDto(" Français ", null),
                        new ProfileSpokenLanguageDto("Français", null),
                        new ProfileSpokenLanguageDto("FRANCAIS", null),
                        new ProfileSpokenLanguageDto("English", "expert"),
                        new ProfileSpokenLanguageDto(" english ", "beginner")
                )
        );
        assertEquals(
                List.of(
                        new ProfileSpokenLanguageDto("Français", null),
                        new ProfileSpokenLanguageDto("English", "expert")
                ),
                result
        );
    }

    @Test
    @DisplayName("normalizeGender maps Male and Female variants")
    void normalizeGender_mapsVariants() {
        assertNull(ProfileExtensionsSupport.normalizeGender("   "));
        assertNull(ProfileExtensionsSupport.normalizeGender("they/them"));
        assertEquals("Male", ProfileExtensionsSupport.normalizeGender(" homme "));
        assertEquals("Female", ProfileExtensionsSupport.normalizeGender("FEMME"));
        assertEquals("Male", ProfileExtensionsSupport.normalizeGender("male"));
        assertEquals("Female", ProfileExtensionsSupport.normalizeGender("Woman"));
    }

    @Test
    @DisplayName("normalizeTeamMembers normalizes fields, links and ordering")
    void normalizeTeamMembers_happyPath() {
        UUID userId = UUID.randomUUID();
        ProfileTeamMemberDto second = new ProfileTeamMemberDto(
                null,
                2,
                " Alice ",
                " Direction artistique ",
                " https://cdn.example.com/content/public/" + userId + "/alice.jpg ",
                List.of(new ProfileTeamSocialLinkDto(
                        null, "email", " Contact ", "mailto:alice@example.com", -1))
        );
        ProfileTeamMemberDto first = new ProfileTeamMemberDto(
                UUID.randomUUID(), 1, "Bob", "Production", null, null);

        List<ProfileTeamMemberDto> result =
                ProfileExtensionsSupport.normalizeTeamMembers(List.of(second, first), userId);

        assertEquals(List.of("Bob", "Alice"), result.stream().map(ProfileTeamMemberDto::name).toList());
        assertNotNull(result.get(1).id());
        assertEquals("Direction artistique", result.get(1).responsibility());
        assertEquals("EMAIL", result.get(1).socialLinks().getFirst().platform());
        assertEquals("Contact", result.get(1).socialLinks().getFirst().label());
    }

    @Test
    @DisplayName("normalizeTeamMembers enforces max 12 items and 6 links")
    void normalizeTeamMembers_limits() {
        UUID userId = UUID.randomUUID();
        ProfileTeamMemberDto member = new ProfileTeamMemberDto(
                null, 0, "Name", "Responsibility", null, List.of());
        assertThrows(BusinessException.class, () -> ProfileExtensionsSupport.normalizeTeamMembers(
                Collections.nCopies(13, member), userId));

        ProfileTeamSocialLinkDto link = new ProfileTeamSocialLinkDto(
                null, "WEBSITE", null, "https://example.com", 0);
        ProfileTeamMemberDto tooManyLinks = new ProfileTeamMemberDto(
                null, 0, "Name", "Responsibility", null, Collections.nCopies(7, link));
        assertThrows(BusinessException.class, () ->
                ProfileExtensionsSupport.normalizeTeamMembers(List.of(tooManyLinks), userId));
    }

    @Test
    @DisplayName("normalizeAboutUs returns null for empty payload")
    void normalizeAboutUs_emptyReturnsNull() {
        UUID userId = UUID.randomUUID();
        assertNull(ProfileExtensionsSupport.normalizeAboutUs(null, userId));
        assertNull(ProfileExtensionsSupport.normalizeAboutUs(
                new ProfileAboutUsDto(null, null, null, null, null, null), userId));
        assertNull(ProfileExtensionsSupport.normalizeAboutUs(
                new ProfileAboutUsDto("  ", " ", List.of("  "), List.of(), "   ",
                        new ProfileAboutUsFounderDto("  ", " ", " ")),
                userId));
    }

    @Test
    @DisplayName("normalizeAboutUs trims fields and keeps optional lists")
    void normalizeAboutUs_trims() {
        UUID userId = UUID.randomUUID();
        String logo = "https://cdn.example.com/content/public/" + userId + "/logo.png";
        String image = "https://cdn.example.com/content/public/" + userId + "/about.jpg";
        ProfileAboutUsDto result = ProfileExtensionsSupport.normalizeAboutUs(
                new ProfileAboutUsDto(
                        "  Studio  ",
                        "  Description  ",
                        List.of("  Task one  ", " ", "Task two"),
                        List.of("  " + image + "  "),
                        "  Quote  ",
                        new ProfileAboutUsFounderDto("  " + logo + "  ", "  Ada  ", "  Founder  ")),
                userId);

        assertNotNull(result);
        assertEquals("Studio", result.title());
        assertEquals("Description", result.description());
        assertEquals(List.of("Task one", "Task two"), result.tasks());
        assertEquals(List.of(image), result.imageUrls());
        assertEquals("Quote", result.quote());
        assertEquals(logo, result.founder().logoUrl());
        assertEquals("Ada", result.founder().name());
        assertEquals("Founder", result.founder().function());
    }

    @Test
    @DisplayName("normalizeAboutUs enforces max 2 images and 12 tasks")
    void normalizeAboutUs_limits() {
        UUID userId = UUID.randomUUID();
        String image1 = "https://cdn.example.com/content/public/" + userId + "/a.jpg";
        String image2 = "https://cdn.example.com/content/public/" + userId + "/b.jpg";
        String image3 = "https://cdn.example.com/content/public/" + userId + "/c.jpg";

        ProfileAboutUsDto twoImages = ProfileExtensionsSupport.normalizeAboutUs(
                new ProfileAboutUsDto("Title", null, null, List.of(image1, image2), null, null),
                userId);
        assertEquals(List.of(image1, image2), twoImages.imageUrls());

        assertThrows(BusinessException.class, () -> ProfileExtensionsSupport.normalizeAboutUs(
                new ProfileAboutUsDto("Title", null, null, List.of(image1, image2, image3), null, null),
                userId));

        List<String> twelveTasks = Collections.nCopies(12, "Task");
        ProfileAboutUsDto twelve = ProfileExtensionsSupport.normalizeAboutUs(
                new ProfileAboutUsDto(null, null, twelveTasks, null, null, null), userId);
        assertEquals(12, twelve.tasks().size());

        assertThrows(BusinessException.class, () -> ProfileExtensionsSupport.normalizeAboutUs(
                new ProfileAboutUsDto(null, null, Collections.nCopies(13, "Task"), null, null, null),
                userId));
    }

    @Test
    @DisplayName("normalizeGalleryItems validates ownership and infers video media")
    void normalizeGalleryItems_happyPath() {
        UUID userId = UUID.randomUUID();
        String mediaUrl = "https://cdn.example.com/content/public/" + userId + "/demo.mp4?version=1";
        ProfileGalleryItemDto item = new ProfileGalleryItemDto(null, -1, " Démo ", mediaUrl, null);

        List<ProfileGalleryItemDto> result =
                ProfileExtensionsSupport.normalizeGalleryItems(List.of(item), userId);

        assertEquals(1, result.size());
        assertNotNull(result.getFirst().id());
        assertEquals(0, result.getFirst().sortOrder());
        assertEquals("Démo", result.getFirst().title());
        assertEquals("VIDEO", result.getFirst().mediaType());
    }

    @Test
    @DisplayName("normalizeGalleryItems allows empty optional title")
    void normalizeGalleryItems_optionalTitle() {
        UUID userId = UUID.randomUUID();
        String mediaUrl = "https://cdn.example.com/content/public/" + userId + "/shot.jpg";
        ProfileGalleryItemDto item = new ProfileGalleryItemDto(null, 0, "  ", mediaUrl, "IMAGE");

        List<ProfileGalleryItemDto> result =
                ProfileExtensionsSupport.normalizeGalleryItems(List.of(item), userId);

        assertEquals(1, result.size());
        assertEquals("", result.getFirst().title());
        assertEquals("IMAGE", result.getFirst().mediaType());
    }

    @Test
    @DisplayName("normalizeGalleryItems allows external media URLs")
    void normalizeGalleryItems_externalUrl() {
        UUID userId = UUID.randomUUID();
        String mediaUrl = "https://images.example.com/photos/portrait.jpg";
        ProfileGalleryItemDto item = new ProfileGalleryItemDto(null, 0, "", mediaUrl, null);

        List<ProfileGalleryItemDto> result =
                ProfileExtensionsSupport.normalizeGalleryItems(List.of(item), userId);

        assertEquals(1, result.size());
        assertEquals("", result.getFirst().title());
        assertEquals(mediaUrl, result.getFirst().mediaUrl());
        assertEquals("IMAGE", result.getFirst().mediaType());
    }

    @Test
    @DisplayName("normalizeGalleryItems enforces max 24 items")
    void normalizeGalleryItems_maxTwentyFour() {
        UUID userId = UUID.randomUUID();
        ProfileGalleryItemDto item = new ProfileGalleryItemDto(
                null,
                0,
                "Image",
                "https://cdn.example.com/content/public/" + userId + "/image.jpg",
                "IMAGE"
        );

        assertThrows(BusinessException.class, () -> ProfileExtensionsSupport.normalizeGalleryItems(
                Collections.nCopies(25, item), userId));
    }

    @Test
    @DisplayName("normalizePortfolioWorks validates fields, stack, link and ordering")
    void normalizePortfolioWorks_happyPath() {
        UUID userId = UUID.randomUUID();
        String imageUrl = "https://cdn.example.com/content/public/" + userId + "/work.jpg";
        ProfilePortfolioWorkDto second = new ProfilePortfolioWorkDto(
                null,
                2,
                " Branding ",
                " Business ",
                " Campaign ",
                "  A description  ",
                List.of(" React ", "react", " Tailwind ", " "),
                "  " + imageUrl + "  ",
                " https://example.com/case "
        );
        ProfilePortfolioWorkDto first = new ProfilePortfolioWorkDto(
                UUID.randomUUID(),
                1,
                "UI",
                "Lifestyle",
                "Landing",
                null,
                List.of(),
                imageUrl,
                "  "
        );

        List<ProfilePortfolioWorkDto> result =
                ProfileExtensionsSupport.normalizePortfolioWorks(List.of(second, first), userId);

        assertEquals(2, result.size());
        assertEquals("Landing", result.getFirst().title());
        assertEquals("UI", result.getFirst().role());
        assertEquals("Lifestyle", result.getFirst().category());
        assertNull(result.getFirst().link());
        assertEquals("Campaign", result.get(1).title());
        assertEquals("Branding", result.get(1).role());
        assertEquals("Business", result.get(1).category());
        assertEquals("A description", result.get(1).description());
        assertEquals(List.of("React", "Tailwind"), result.get(1).stack());
        assertEquals(imageUrl, result.get(1).imageUrl());
        assertEquals("https://example.com/case", result.get(1).link());
        assertNotNull(result.get(1).id());
    }

    @Test
    @DisplayName("normalizePortfolioWorks requires title and imageUrl")
    void normalizePortfolioWorks_requiredFields() {
        UUID userId = UUID.randomUUID();
        String imageUrl = "https://cdn.example.com/content/public/" + userId + "/work.jpg";

        assertThrows(BusinessException.class, () -> ProfileExtensionsSupport.normalizePortfolioWorks(
                List.of(new ProfilePortfolioWorkDto(null, 0, null, null, "  ", null, null, imageUrl, null)),
                userId));
        assertThrows(BusinessException.class, () -> ProfileExtensionsSupport.normalizePortfolioWorks(
                List.of(new ProfilePortfolioWorkDto(null, 0, null, null, "Title", null, null, "  ", null)),
                userId));
    }

    @Test
    @DisplayName("normalizePortfolioWorks enforces max 6 items and rejects unsafe links")
    void normalizePortfolioWorks_limitsAndLink() {
        UUID userId = UUID.randomUUID();
        String imageUrl = "https://cdn.example.com/content/public/" + userId + "/work.jpg";
        ProfilePortfolioWorkDto item = new ProfilePortfolioWorkDto(
                null, 0, "Role", "Business", "Title", null, List.of(), imageUrl, null);

        assertThrows(BusinessException.class, () -> ProfileExtensionsSupport.normalizePortfolioWorks(
                Collections.nCopies(7, item), userId));

        assertThrows(BusinessException.class, () -> ProfileExtensionsSupport.normalizePortfolioWorks(
                List.of(new ProfilePortfolioWorkDto(
                        null, 0, null, null, "Title", null, null, imageUrl, "javascript:alert(1)")),
                userId));
    }

    @Test
    @DisplayName("normalizeContactEntries trims, assigns ids and reindexes sortOrder")
    void normalizeContactEntries_happyPath() {
        ProfileContactEntryDto keptId = new ProfileContactEntryDto(
                UUID.fromString("11111111-1111-1111-1111-111111111111"), 9, "  Paris ");
        ProfileContactEntryDto blank = new ProfileContactEntryDto(null, 0, "   ");
        ProfileContactEntryDto generated = new ProfileContactEntryDto(null, -1, "Lyon");

        List<ProfileContactEntryDto> result = ProfileExtensionsSupport.normalizeContactEntries(
                List.of(keptId, blank, generated),
                ProfileExtensionsSupport.MAX_CONTACT_ENTRIES,
                ProfileExtensionsSupport.MAX_CONTACT_ADDRESS,
                "CONTACT_ADDRESSES");

        assertEquals(2, result.size());
        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), result.get(0).id());
        assertEquals(0, result.get(0).sortOrder());
        assertEquals("Paris", result.get(0).value());
        assertNotNull(result.get(1).id());
        assertEquals(1, result.get(1).sortOrder());
        assertEquals("Lyon", result.get(1).value());
    }

    @Test
    @DisplayName("normalizeContactEntries enforces max and email format")
    void normalizeContactEntries_limitsAndEmail() {
        ProfileContactEntryDto entry = new ProfileContactEntryDto(null, 0, "a@b.co");
        assertThrows(BusinessException.class, () -> ProfileExtensionsSupport.normalizeContactEntries(
                Collections.nCopies(9, entry),
                ProfileExtensionsSupport.MAX_CONTACT_ENTRIES,
                ProfileExtensionsSupport.MAX_CONTACT_EMAIL,
                "CONTACT_EMAILS",
                true));

        assertThrows(BusinessException.class, () -> ProfileExtensionsSupport.normalizeContactEntries(
                List.of(new ProfileContactEntryDto(null, 0, "not-an-email")),
                ProfileExtensionsSupport.MAX_CONTACT_ENTRIES,
                ProfileExtensionsSupport.MAX_CONTACT_EMAIL,
                "CONTACT_EMAILS",
                true));
    }

    @Test
    @DisplayName("contactEntriesForResponse synthesizes from legacy when list empty")
    void contactEntriesForResponse_synthesizesLegacy() {
        List<ProfileContactEntryDto> synthesized =
                ProfileExtensionsSupport.contactEntriesForResponse(List.of(), "  hello@example.com ");
        assertEquals(1, synthesized.size());
        assertEquals("hello@example.com", synthesized.getFirst().value());
        assertEquals(0, synthesized.getFirst().sortOrder());
        assertNotNull(synthesized.getFirst().id());
    }

    @Test
    @DisplayName("normalizeAboutStringList trims, skips blanks and enforces limits")
    void normalizeAboutStringList_happyPathAndLimits() {
        List<String> result = ProfileExtensionsSupport.normalizeAboutStringList(
                List.of("  React  ", " ", "TypeScript"),
                ProfileExtensionsSupport.MAX_ABOUT_SKILLS,
                ProfileExtensionsSupport.MAX_ABOUT_STRING_LENGTH,
                "ABOUT_SKILLS");
        assertEquals(List.of("React", "TypeScript"), result);

        assertThrows(BusinessException.class, () -> ProfileExtensionsSupport.normalizeAboutStringList(
                Collections.nCopies(13, "Skill"),
                ProfileExtensionsSupport.MAX_ABOUT_SKILLS,
                ProfileExtensionsSupport.MAX_ABOUT_STRING_LENGTH,
                "ABOUT_SKILLS"));

        String tooLong = "x".repeat(ProfileExtensionsSupport.MAX_ABOUT_STRING_LENGTH + 1);
        assertThrows(BusinessException.class, () -> ProfileExtensionsSupport.normalizeAboutStringList(
                List.of(tooLong),
                ProfileExtensionsSupport.MAX_ABOUT_SKILLS,
                ProfileExtensionsSupport.MAX_ABOUT_STRING_LENGTH,
                "ABOUT_SKILLS"));
    }

    @Test
    @DisplayName("normalizeAboutSkillEntries assigns ids, sorts and enforces limits")
    void normalizeAboutSkillEntries_happyPathAndLimits() {
        ProfileSkillEntryDto second = new ProfileSkillEntryDto(
                null, 2, " APIs ", " REST & GraphQL ");
        ProfileSkillEntryDto first = new ProfileSkillEntryDto(
                UUID.randomUUID(), 1, "Web apps", null);

        List<ProfileSkillEntryDto> result =
                ProfileExtensionsSupport.normalizeAboutSkillEntries(List.of(second, first));

        assertEquals(2, result.size());
        assertEquals("Web apps", result.getFirst().title());
        assertEquals("APIs", result.get(1).title());
        assertEquals("REST & GraphQL", result.get(1).description());
        assertNotNull(result.get(1).id());

        assertThrows(BusinessException.class, () -> ProfileExtensionsSupport.normalizeAboutSkillEntries(
                Collections.nCopies(13, new ProfileSkillEntryDto(null, 0, "Skill", null))));

        assertThrows(BusinessException.class, () -> ProfileExtensionsSupport.normalizeAboutSkillEntries(
                List.of(new ProfileSkillEntryDto(null, 0, null, "Only description"))));
    }

    @Test
    @DisplayName("normalizeAboutEducationEntries assigns ids, sorts and enforces max 8")
    void normalizeAboutEducationEntries_happyPathAndLimits() {
        ProfileEducationEntryDto second = new ProfileEducationEntryDto(
                null, 2, " 2017 — 2019 ", "  MSc Design  ", "  ENSAD  ");
        ProfileEducationEntryDto first = new ProfileEducationEntryDto(
                UUID.randomUUID(), 1, "2014 — 2016", "BA", "University");

        List<ProfileEducationEntryDto> result =
                ProfileExtensionsSupport.normalizeAboutEducationEntries(List.of(second, first));

        assertEquals(2, result.size());
        assertEquals("BA", result.getFirst().title());
        assertEquals("2017 — 2019", result.get(1).schoolYear());
        assertEquals("MSc Design", result.get(1).title());
        assertEquals("ENSAD", result.get(1).institution());
        assertNotNull(result.get(1).id());

        assertThrows(BusinessException.class, () -> ProfileExtensionsSupport.normalizeAboutEducationEntries(
                Collections.nCopies(9, new ProfileEducationEntryDto(null, 0, null, "Title", null))));

        assertThrows(BusinessException.class, () -> ProfileExtensionsSupport.normalizeAboutEducationEntries(
                List.of(new ProfileEducationEntryDto(
                        null, 0, "x".repeat(41), null, null))));
    }

    private static FaqItemDto faq(int order) {
        return new FaqItemDto(UUID.randomUUID(), order, "Q" + order, "A" + order);
    }

    private static ProfileServiceDto service(int order) {
        return new ProfileServiceDto(UUID.randomUUID(), order, "Service " + order, "Desc", 1000, "3 days", List.of());
    }

    private static ProfileLinkDto link(int order) {
        return new ProfileLinkDto(
                UUID.randomUUID(), "CUSTOM", "Link " + order, "https://example.com/" + order, order, null, null);
    }
}
