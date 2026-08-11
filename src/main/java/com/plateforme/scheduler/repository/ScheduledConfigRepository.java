package com.plateforme.scheduler.repository;

import com.plateforme.scheduler.entity.ScheduledConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScheduledConfigRepository extends JpaRepository<ScheduledConfig, UUID> {

    Optional<ScheduledConfig> findByNicheRequest_Id(UUID nicheRequestId);

    Optional<ScheduledConfig> findByNicheRequest_IdAndClient_Id(UUID nicheRequestId, UUID clientId);
}
