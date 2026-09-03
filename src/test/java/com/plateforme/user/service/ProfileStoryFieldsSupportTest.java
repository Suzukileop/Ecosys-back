package com.plateforme.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plateforme.shared.exception.BusinessException;
import com.plateforme.user.dto.ProfileMediaBlock;
import com.plateforme.user.dto.ProfileStrengthToolDto;
import com.plateforme.user.dto.ProfileToolRefDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileStoryFieldsSupportTest {

  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("strength tool deserializer accepts legacy strings")
  void strengthToolDeserializer_acceptsLegacyString() throws Exception {
    ProfileStrengthToolDto result = objectMapper.readValue("\"Premiere\"", ProfileStrengthToolDto.class);

    assertEquals("Premiere", result.name());
    assertNull(result.description());
    assertNull(result.category());
  }

  @Test
  @DisplayName("strength tool deserializer reads metadata and ignores unknown fields")
  void strengthToolDeserializer_readsMetadata() throws Exception {
    String json = """
        {
          "name": "React",
          "description": "UI",
          "category": "frontend",
          "level": "advanced",
          "useCases": ["Dashboards", "", 42],
          "experienceYears": 5,
          "experienceLabel": "5 ans",
          "currentlyUsed": true,
          "futureField": "ignored"
        }
        """;

    ProfileStrengthToolDto result = objectMapper.readValue(json, ProfileStrengthToolDto.class);

    assertEquals("React", result.name());
    assertEquals("UI", result.description());
    assertEquals("frontend", result.category());
    assertEquals("advanced", result.level());
    assertEquals(List.of("Dashboards"), result.useCases());
    assertEquals(5, result.experienceYears());
    assertEquals("5 ans", result.experienceLabel());
    assertEquals(true, result.currentlyUsed());
  }

  @Test
  @DisplayName("normalizeBlocks assigns ids and sorts by sortOrder")
  void normalizeBlocks_sortsAndAssignsIds() {
    UUID id = UUID.randomUUID();
    List<ProfileMediaBlock> result = ProfileStoryFieldsSupport.normalizeBlocks(
        List.of(
            new ProfileMediaBlock(id, 1, "Second", null, null),
            new ProfileMediaBlock(null, 0, "First", null, null)
        ),
        USER_ID
    );

    assertEquals(2, result.size());
    assertEquals("First", result.get(0).text());
    assertEquals("Second", result.get(1).text());
    assertEquals(id, result.get(1).id());
  }

  @Test
  @DisplayName("normalizeBlocks rejects mismatched media fields")
  void normalizeBlocks_rejectsPartialMedia() {
    assertThrows(BusinessException.class, () -> ProfileStoryFieldsSupport.normalizeBlocks(
        List.of(new ProfileMediaBlock(UUID.randomUUID(), 0, "Text", "https://example.com/x", null)),
        USER_ID
    ));
  }

  @Test
  @DisplayName("normalizeStrengths trims and deduplicates")
  void normalizeStrengths_dedupes() {
    List<ProfileStrengthToolDto> result = ProfileStoryFieldsSupport.normalizeStrengths(
        List.of(
            new ProfileStrengthToolDto(" Premiere ", "Main editor"),
            new ProfileStrengthToolDto("Premiere", "Ignored duplicate"),
            new ProfileStrengthToolDto("React", null)
        ),
        USER_ID
    );
    assertEquals(2, result.size());
    assertEquals("Premiere", result.get(0).name());
    assertEquals("Main editor", result.get(0).description());
    assertEquals("React", result.get(1).name());
    assertNull(result.get(1).description());
  }

  @Test
  @DisplayName("normalizeStrengths sanitizes tool metadata")
  void normalizeStrengths_sanitizesMetadata() {
    ProfileStrengthToolDto result = ProfileStoryFieldsSupport.normalizeStrengths(
        List.of(new ProfileStrengthToolDto(
            " React ",
            " UI development ",
            " frontend ".repeat(12),
            " ADVANCED ",
            List.of(" Dashboards ", "", "x".repeat(70), "dashboards",
                "APIs", "Forms", "Testing", "SSR", "Accessibility", "Animations"),
            99,
            " Used professionally ".repeat(8),
            null,
            null
        )),
        USER_ID
    ).getFirst();

    assertEquals("React", result.name());
    assertEquals("UI development", result.description());
    assertEquals(80, result.category().length());
    assertEquals("advanced", result.level());
    assertEquals(8, result.useCases().size());
    assertEquals(60, result.useCases().get(1).length());
    assertEquals(40, result.experienceYears());
    assertEquals(80, result.experienceLabel().length());
    assertNull(result.currentlyUsed());
  }

  @Test
  @DisplayName("normalizeSubtitles trims and filters empty values")
  void normalizeSubtitles_trims() {
    List<String> result = ProfileStoryFieldsSupport.normalizeSubtitles(
        List.of(" First ", "", "Second")
    );
    assertEquals(List.of("First", "Second"), result);
  }

  @Test
  @DisplayName("normalizePeriod trims, blank to null, and enforces max length")
  void normalizePeriod_trimsAndValidates() {
    assertEquals("2020-2023", ProfileStoryFieldsSupport.normalizePeriod(" 2020-2023 "));
    assertNull(ProfileStoryFieldsSupport.normalizePeriod("   "));
    assertNull(ProfileStoryFieldsSupport.normalizePeriod(null));
  }

  @Test
  @DisplayName("normalizePeriod rejects values over 80 characters")
  void normalizePeriod_rejectsTooLong() {
    assertThrows(BusinessException.class,
        () -> ProfileStoryFieldsSupport.normalizePeriod("x".repeat(81)));
  }

  @Test
  @DisplayName("normalizeBlocks preserves normalized period")
  void normalizeBlocks_preservesPeriod() {
    List<ProfileMediaBlock> result = ProfileStoryFieldsSupport.normalizeBlocks(
        List.of(new ProfileMediaBlock(UUID.randomUUID(), 0, "Title", null, null, " 2020-2023 ")),
        USER_ID
    );
    assertEquals("2020-2023", result.get(0).period());
  }

  @Test
  @DisplayName("normalizeBlocks clears blank period")
  void normalizeBlocks_clearsBlankPeriod() {
    List<ProfileMediaBlock> result = ProfileStoryFieldsSupport.normalizeBlocks(
        List.of(new ProfileMediaBlock(UUID.randomUUID(), 0, "Title", null, null, "   ")),
        USER_ID
    );
    assertNull(result.get(0).period());
  }

  @Test
  @DisplayName("normalizeBlocks preserves experience enrichment fields")
  void normalizeBlocks_preservesExperienceEnrichment() {
    UUID linkId = UUID.randomUUID();
    List<ProfileMediaBlock> result = ProfileStoryFieldsSupport.normalizeBlocks(
        List.of(new ProfileMediaBlock(
            UUID.randomUUID(),
            0,
            "Editor",
            "Studio",
            "Description",
            null,
            null,
            "2021 — present",
            "ongoing",
            List.of(" Cut reels ", "Color grade"),
            List.of(
                new ProfileToolRefDto(" Adobe Premiere Pro "),
                new ProfileToolRefDto("After Effects")
            ),
            List.of(new com.plateforme.user.dto.ExperienceProofLink(
                linkId, "Repo", "https://github.com/example/project", "github", 0)),
            " Remote ",
            "freelance"
        )),
        USER_ID
    );

    ProfileMediaBlock block = result.get(0);
    assertEquals("ONGOING", block.status());
    assertEquals(List.of("Cut reels", "Color grade"), block.tasks());
    assertEquals(2, block.tools().size());
    assertEquals("Adobe Premiere Pro", block.tools().get(0).name());
    assertEquals("After Effects", block.tools().get(1).name());
    assertNull(block.tools().get(0).iconUrl());
    assertEquals(1, block.links().size());
    assertEquals(linkId, block.links().get(0).id());
    assertEquals("GITHUB", block.links().get(0).platform());
    assertEquals("Remote", block.location());
    assertEquals("FREELANCE", block.employmentType());
  }

  @Test
  @DisplayName("hasProfileStack detects named stack items")
  void hasProfileStack_detectsNamedItems() {
    assertFalse(ProfileStoryFieldsSupport.hasProfileStack(null));
    assertFalse(ProfileStoryFieldsSupport.hasProfileStack(List.of()));
    assertFalse(ProfileStoryFieldsSupport.hasProfileStack(
        List.of(new ProfileStrengthToolDto(" ", null))));
    assertTrue(ProfileStoryFieldsSupport.hasProfileStack(
        List.of(new ProfileStrengthToolDto("React", null))));
  }

  @Test
  @DisplayName("normalizeBlocks rejects invalid status")
  void normalizeBlocks_rejectsInvalidStatus() {
    assertThrows(BusinessException.class, () -> ProfileStoryFieldsSupport.normalizeBlocks(
        List.of(new ProfileMediaBlock(
            UUID.randomUUID(), 0, null, null, "Text", null, null, null,
            "ACTIVE", List.of(), List.of(), List.of(), null, null)),
        USER_ID
    ));
  }
}
