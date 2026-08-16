package com.plateforme.user.repository;

import com.plateforme.user.entity.CreatorProfileVisit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CreatorProfileVisitRepository extends JpaRepository<CreatorProfileVisit, UUID> {

    Optional<CreatorProfileVisit> findByCreatorUserIdAndVisitorKey(UUID creatorUserId, String visitorKey);

    Page<CreatorProfileVisit> findByCreatorUserIdOrderByViewedAtDesc(UUID creatorUserId, Pageable pageable);

    long countByCreatorUserId(UUID creatorUserId);
}
