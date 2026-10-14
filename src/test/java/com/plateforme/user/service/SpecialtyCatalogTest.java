package com.plateforme.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpecialtyCatalogTest {

    @Test
    void catalogHasHundredsOfUniqueLabels() {
        assertThat(SpecialtyCatalog.all()).hasSizeGreaterThan(500).doesNotHaveDuplicates();
    }

    @Test
    void searchRanksPrefixMatchesFirst() {
        assertThat(SpecialtyCatalog.search("data", 30).get(0)).startsWith("Data");
        assertThat(SpecialtyCatalog.search("data", 30))
                .contains("Data engineer", "Big data engineer", "Data visualization");
    }

    @Test
    void searchMatchesWordPrefixAndIgnoresAccents() {
        assertThat(SpecialtyCatalog.search("photographe", 50)).contains("Wedding photographer");
        assertThat(SpecialtyCatalog.search("rédaction", 50)).contains("Copywriter", "SEO writer");
    }

    @Test
    void searchExpandsFieldKeywords() {
        assertThat(SpecialtyCatalog.search("musique", 60)).contains("Beatmaker", "Mixing engineer");
    }

    @Test
    void blankQueryReturnsNothing() {
        assertThat(SpecialtyCatalog.search("  ", 30)).isEmpty();
    }
}
