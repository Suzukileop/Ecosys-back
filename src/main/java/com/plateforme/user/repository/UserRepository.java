package com.plateforme.user.repository;

import com.plateforme.user.entity.User;
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
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByAuthProviderAndProviderUserId(String authProvider, String providerUserId);

    boolean existsByEmail(String email);

    Page<User> findAllByDeletedAtIsNull(Pageable pageable);

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    List<User> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);

    @Query("""
            SELECT DISTINCT u FROM User u JOIN u.roles r
            WHERE u.deletedAt IS NULL AND r.name = :roleName
            """)
    List<User> findByRoleNameAndDeletedAtIsNull(@Param("roleName") String roleName);

    @Query("""
            SELECT u FROM User u
            WHERE u.deletedAt IS NULL
              AND u.id <> :excludeId
              AND LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
            """)
    Page<User> searchByFullNameExcluding(
            @Param("q") String q,
            @Param("excludeId") UUID excludeId,
            Pageable pageable);

    long countByDeletedAtIsNull();
}

