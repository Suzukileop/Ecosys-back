package com.plateforme.user.controller;

import com.plateforme.user.dto.LanguageProficiencyLevelDto;
import com.plateforme.user.repository.LanguageProficiencyLevelRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reference")
@RequiredArgsConstructor
public class ReferenceController {

    private final LanguageProficiencyLevelRepository languageProficiencyLevelRepository;

    @Operation(summary = "List spoken-language proficiency levels (beginner → expert)")
    @GetMapping("/language-proficiency-levels")
    public List<LanguageProficiencyLevelDto> languageProficiencyLevels() {
        return languageProficiencyLevelRepository.findAllByOrderBySortOrderAsc().stream()
                .map(level -> new LanguageProficiencyLevelDto(
                        level.getCode(),
                        level.getLabel(),
                        level.getSortOrder()))
                .toList();
    }
}
