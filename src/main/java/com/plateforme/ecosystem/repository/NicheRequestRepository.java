package com.plateforme.ecosystem.repository;

import com.plateforme.ecosystem.entity.NicheRequest;
import com.plateforme.ecosystem.entity.NicheStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NicheRequestRepository extends JpaRepository<NicheRequest, UUID> {

    boolean existsByUniqueCode(String uniqueCode);

    Optional<NicheRequest> findByIdAndClient_Id(UUID id, UUID clientId);

    Page<NicheRequest> findByClient_Id(UUID clientId, Pageable pageable);

    Page<NicheRequest> findByClient_IdAndStatus(UUID clientId, NicheStatus status, Pageable pageable);

    Page<NicheRequest> findByStatusAndBotConfirmedIsTrueOrderByCreatedAtAsc(NicheStatus status, Pageable pageable);

    Page<NicheRequest> findByStatusOrderByActivatedAtDesc(NicheStatus status, Pageable pageable);

    Optional<NicheRequest> findByVpiReference(String vpiReference);
}
