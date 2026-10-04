package com.plateforme.user.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CreatorSearchExpandTest {

    @Test
    void expandedTerms_developerIncludesTools() {
        List<String> terms = CreatorSearchExpand.expandedTerms("developer");
        assertThat(terms).isNotEmpty();
        assertThat(terms.stream().map(String::toLowerCase).toList())
                .anySatisfy(t -> assertThat(t).containsAnyOf("react", "spring", "developer"));
        assertThat(terms).anyMatch(t -> t.equalsIgnoreCase("Developer") || t.equalsIgnoreCase("developer"));
    }

    @Test
    void expandedTerms_reactMapsTowardDeveloper() {
        List<String> terms = CreatorSearchExpand.expandedTerms("react");
        assertThat(terms).anyMatch(t -> t.equalsIgnoreCase("Developer"));
    }

    @Test
    void expandedTerms_aiMapsToAiDomainNotDataScience() {
        List<String> terms = CreatorSearchExpand.expandedTerms("AI");
        String joined = String.join("|", terms).toLowerCase();
        assertThat(joined).contains("machine learning");
        assertThat(joined).containsAnyOf("llm", "chatgpt", "prompt");
    }

    @Test
    void expandedTerms_dataAnalystIncludesBiTools() {
        List<String> terms = CreatorSearchExpand.expandedTerms("Data analyst");
        String joined = String.join("|", terms).toLowerCase();
        assertThat(joined).containsAnyOf("power bi", "tableau", "sql", "dashboard");
    }

    @Test
    void specialtySignals_developerIsNonEmptyWithRelatedTerms() {
        List<String> signals = CreatorSearchExpand.specialtySignals("Developer");
        assertThat(signals).isNotEmpty();
        String joined = String.join("|", signals).toLowerCase();
        assertThat(joined).contains("developer");
        assertThat(joined).containsAnyOf("react", "spring", "java", "software");
        assertThat(CreatorSearchExpand.specialtySignalsPipe("Developer")).contains("|");
    }
}
