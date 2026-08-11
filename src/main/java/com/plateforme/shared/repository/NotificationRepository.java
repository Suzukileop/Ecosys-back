package com.plateforme.shared.repository;

import com.plateforme.shared.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByIsReadAscCreatedAtDesc(UUID userId, Pageable pageable);

    List<Notification> findTop5ByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserIdAndIsReadFalse(UUID userId);

    List<Notification> findByUserId(UUID userId);
}
