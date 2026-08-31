package com.plateforme.user.repository;

import com.plateforme.user.entity.LanguageProficiencyLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LanguageProficiencyLevelRepository extends JpaRepository<LanguageProficiencyLevel, UUID> {

    List<LanguageProficiencyLevel> findAllByOrderBySortOrderAsc();
}
