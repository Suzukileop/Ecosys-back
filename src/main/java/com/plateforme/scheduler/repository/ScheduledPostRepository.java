package com.plateforme.scheduler.repository;

import com.plateforme.scheduler.entity.PostStatus;
import com.plateforme.scheduler.entity.ScheduledPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduledPostRepository extends JpaRepository<ScheduledPost, UUID> {

    Page<ScheduledPost> findByClient_Id(UUID clientId, Pageable pageable);

    Page<ScheduledPost> findByClient_IdAndStatus(UUID clientId, PostStatus status, Pageable pageable);

    List<ScheduledPost> findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            PostStatus status,
            LocalDateTime now);

    long countByClient_Id(UUID clientId);

    long countByClient_IdAndStatus(UUID clientId, PostStatus status);

    long countByStatus(PostStatus status);

    List<ScheduledPost> findByClient_IdAndStatusAndPublishedAtBetween(
            UUID clientId,
            PostStatus status,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive);

    Page<ScheduledPost> findByNicheRequest_IdAndClient_Id(
            UUID nicheRequestId,
            UUID clientId,
            Pageable pageable);

    @Query("SELECT COALESCE(MAX(sp.deliveryNumber), 0) FROM ScheduledPost sp WHERE sp.nicheRequest.id = :nicheRequestId")
    int findMaxDeliveryNumberByNicheRequestId(@Param("nicheRequestId") UUID nicheRequestId);

    @Query("SELECT sp.platform, COUNT(sp) FROM ScheduledPost sp WHERE sp.client.id = :clientId GROUP BY sp.platform")
    List<Object[]> countPostsByPlatformForClient(@Param("clientId") UUID clientId);
}
