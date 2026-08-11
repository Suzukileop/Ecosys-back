package com.plateforme.shared.repository;

import com.plateforme.shared.entity.PlatformConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformConfigRepository extends JpaRepository<PlatformConfig, String> {
}

