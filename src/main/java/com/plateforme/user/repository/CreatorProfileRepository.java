package com.plateforme.user.repository;

import com.plateforme.user.entity.CreatorProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreatorProfileRepository extends JpaRepository<CreatorProfile, UUID> {

    Optional<CreatorProfile> findByUserId(UUID userId);

    List<CreatorProfile> findByUser_IdIn(Collection<UUID> userIds);

    @Query("""
            SELECT cp FROM CreatorProfile cp JOIN cp.user u
            WHERE u.id = :userId AND u.deletedAt IS NULL
            """)
    Optional<CreatorProfile> findByUserIdAndUserDeletedAtIsNull(@Param("userId") UUID userId);

    Page<CreatorProfile> findBySpecialiteContainingIgnoreCaseAndUser_DeletedAtIsNull(String specialite, Pageable pageable);

    @Query("""
            SELECT cp FROM CreatorProfile cp JOIN cp.user u
            WHERE u.deletedAt IS NULL
            AND COALESCE(cp.appRole, 'GENERAL_MEMBER') = 'SERVICE_PROVIDER'
            AND (LOWER(COALESCE(cp.bio, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                 OR LOWER(COALESCE(cp.specialite, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                 OR LOWER(COALESCE(cp.shopName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                 OR LOWER(COALESCE(u.fullName, '')) LIKE LOWER(CONCAT('%', :q, '%')))
            AND (:available IS NULL OR COALESCE(cp.isAvailable, true) = :available)
            """)
    Page<CreatorProfile> searchByBioOrSpecialite(
            @Param("q") String q,
            @Param("available") Boolean available,
            Pageable pageable);

    @Query("""
            SELECT cp FROM CreatorProfile cp JOIN cp.user u
            WHERE u.deletedAt IS NULL
            AND COALESCE(cp.appRole, 'GENERAL_MEMBER') = 'SERVICE_PROVIDER'
            AND (:specialite IS NULL OR :specialite = '' OR LOWER(cp.specialite) LIKE LOWER(CONCAT('%', :specialite, '%')))
            AND (:verified IS NULL OR cp.isVerified = :verified)
            AND (:available IS NULL OR COALESCE(cp.isAvailable, true) = :available)
            ORDER BY u.fullName ASC
            """)
    Page<CreatorProfile> findForMarketplace(
            @Param("specialite") String specialite,
            @Param("verified") Boolean verified,
            @Param("available") Boolean available,
            Pageable pageable);
}
