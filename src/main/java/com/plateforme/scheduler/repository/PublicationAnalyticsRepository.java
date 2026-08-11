package com.plateforme.scheduler.repository;

import com.plateforme.scheduler.entity.PublicationAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PublicationAnalyticsRepository extends JpaRepository<PublicationAnalytics, UUID> {

    Optional<PublicationAnalytics> findByPost_Id(UUID postId);

    List<PublicationAnalytics> findByPost_IdIn(List<UUID> postIds);


    @Query(nativeQuery = true, value = """
            SELECT COALESCE(SUM(pa.views), 0)
            FROM publication_analytics pa
            INNER JOIN scheduled_posts sp ON pa.post_id = sp.id
            WHERE sp.client_id = :clientId
            """)
    Long sumViewsByClientId(@Param("clientId") UUID clientId);

    @Query(nativeQuery = true, value = """
            SELECT COALESCE(SUM(pa.likes), 0)
            FROM publication_analytics pa
            INNER JOIN scheduled_posts sp ON pa.post_id = sp.id
            WHERE sp.client_id = :clientId
            """)
    Long sumLikesByClientId(@Param("clientId") UUID clientId);
}
