package com.plateforme.ecosystem.repository;

import com.plateforme.ecosystem.entity.ServiceRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {

    Page<ServiceRequest> findByClientId(UUID clientId, Pageable pageable);

    boolean existsByUniqueCode(String uniqueCode);

    Optional<ServiceRequest> findByUniqueCode(String uniqueCode);
}
