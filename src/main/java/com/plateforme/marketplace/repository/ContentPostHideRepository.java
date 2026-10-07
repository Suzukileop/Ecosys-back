package com.plateforme.marketplace.repository;

import com.plateforme.marketplace.entity.ContentPostHide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ContentPostHideRepository extends JpaRepository<ContentPostHide, ContentPostHide.Key> {

    boolean existsByUserIdAndPostId(UUID userId, UUID postId);

    void deleteByUserIdAndPostId(UUID userId, UUID postId);
}
