package com.plateforme.user.repository;

import com.plateforme.user.entity.CreatorProfileImage;
import com.plateforme.user.entity.CreatorProfileImageKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreatorProfileImageRepository extends JpaRepository<CreatorProfileImage, UUID> {

    List<CreatorProfileImage> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<CreatorProfileImage> findByUserIdAndKindOrderByCreatedAtDesc(UUID userId, CreatorProfileImageKind kind);

    Optional<CreatorProfileImage> findByUserIdAndKindAndUrl(UUID userId, CreatorProfileImageKind kind, String url);

    Optional<CreatorProfileImage> findByIdAndUserId(UUID id, UUID userId);
}
