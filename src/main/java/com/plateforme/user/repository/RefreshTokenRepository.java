package com.plateforme.user.repository;

import com.plateforme.user.entity.RefreshToken;
import com.plateforme.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findAllByUserAndIsRevokedFalse(User user);

    /** Deletes tokens that can never be accepted again: expired, or revoked before {@code revokedBefore}. */
    @Modifying
    @Query("""
            DELETE FROM RefreshToken t
            WHERE t.expiryDate < :now
            OR (t.isRevoked = true AND (t.revokedAt IS NULL OR t.revokedAt < :revokedBefore))
            """)
    int deleteStale(@Param("now") LocalDateTime now, @Param("revokedBefore") LocalDateTime revokedBefore);
}
