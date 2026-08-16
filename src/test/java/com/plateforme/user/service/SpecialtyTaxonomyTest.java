package com.plateforme.user.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpecialtyTaxonomyTest {

    @Test
    void canonicalize_mapsLegacyLabels() {
        assertThat(SpecialtyTaxonomy.canonicalize("Data Scientist")).isEqualTo("Data science");
        assertThat(SpecialtyTaxonomy.canonicalize("UI/UX")).isEqualTo("UI / UX");
        assertThat(SpecialtyTaxonomy.canonicalize("developer")).isEqualTo("Developer");
    }

    @Test
    void normalizeSpecialties_keepsFreeTextAndCapsAtThree() {
        List<String> result = SpecialtyTaxonomy.normalizeSpecialties(
                List.of("Motion Designer", "DevOps Engineer", "Web Developer", "Music"),
                "DevOps Engineer");
        // "DevOps Engineer" canonicalizes to Popular label "DevOps"
        assertThat(result).containsExactly("DevOps", "Motion Designer", "Web Developer");
    }

    @Test
    void normalizeSpecialties_dedupesIgnoreCaseAndPutsPrimaryFirst() {
        List<String> result = SpecialtyTaxonomy.normalizeSpecialties(
                List.of("Developer", "design", "Developer"),
                "Design");
        assertThat(result).containsExactly("Design", "Developer");
    }

    @Test
    void matchesFilter_containsAndCompact() {
        assertThat(SpecialtyTaxonomy.matchesFilter("Web Developer", "Developer")).isTrue();
        assertThat(SpecialtyTaxonomy.matchesFilter("Frontend Developer", "developer")).isTrue();
        assertThat(SpecialtyTaxonomy.matchesFilter("UI/UX Designer", "UI / UX")).isTrue();
        assertThat(SpecialtyTaxonomy.matchesFilter("Photography", "Developer")).isFalse();
    }

    @Test
    void normalizeTags_dedupesAndCaps() {
        List<String> result = SpecialtyTaxonomy.normalizeTags(List.of("React", "react", "Python"));
        assertThat(result).containsExactly("React", "Python");
    }

    @Test
    void resolveSearchTerms_includesRawAndCanonicalAlias() {
        assertThat(SpecialtyTaxonomy.resolveSearchTerms("dev"))
                .containsExactly("dev", "Developer");
        assertThat(SpecialtyTaxonomy.resolveSearchTerms("Data science"))
                .containsExactly("Data science");
        assertThat(SpecialtyTaxonomy.resolveSearchTerms("  ")).isEmpty();
    }

    @Test
    void primaryAndAlternateSearchTerms_preferCanonical() {
        assertThat(SpecialtyTaxonomy.primarySearchTerm("dev")).isEqualTo("Developer");
        assertThat(SpecialtyTaxonomy.alternateSearchTerm("dev")).isEqualTo("dev");
        assertThat(SpecialtyTaxonomy.primarySearchTerm("Developer")).isEqualTo("Developer");
        assertThat(SpecialtyTaxonomy.alternateSearchTerm("Developer")).isEmpty();
        assertThat(SpecialtyTaxonomy.primarySearchTerm(null)).isNull();
        assertThat(SpecialtyTaxonomy.alternateSearchTerm(null)).isEmpty();
    }

    @Test
    void resolveSearchTerms_uiuxAlias() {
        assertThat(SpecialtyTaxonomy.resolveSearchTerms("uiux"))
                .contains("uiux", "UI / UX");
        assertThat(SpecialtyTaxonomy.primarySearchTerm("uiux")).isEqualTo("UI / UX");
    }
}
