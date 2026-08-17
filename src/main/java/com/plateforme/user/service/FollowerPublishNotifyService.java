package com.plateforme.user.service;

import com.plateforme.shared.service.NotificationService;
import com.plateforme.user.entity.User;
import com.plateforme.user.repository.CreatorFollowRepository;
import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Fan-out PLATFORM notifications to followers when a creator publishes
 * a product, public content, or a new active service.
 *
 * <p>Runs in the caller's transaction (same pattern as profile-visit / purchase
 * notifications). Do not schedule work from {@code afterCommit}: the JPA
 * EntityManager is still thread-bound there, so nested {@code @Transactional}
 * saves can appear to succeed then never persist.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FollowerPublishNotifyService {

    public static final String TYPE_NEW_PRODUCT = "FOLLOWER_NEW_PRODUCT";
    public static final String TYPE_NEW_CONTENT = "FOLLOWER_NEW_CONTENT";
    public static final String TYPE_NEW_SERVICE = "FOLLOWER_NEW_SERVICE";

    private final CreatorFollowRepository creatorFollowRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public void notifyFollowersNewProduct(UUID creatorId, UUID productId, String productTitle) {
        String name = creatorDisplayName(creatorId);
        String title = "New product";
        String item = productTitle != null && !productTitle.isBlank() ? productTitle.trim() : "Untitled";
        String message = name + " published a new product: " + item;
        fanOut(creatorId, TYPE_NEW_PRODUCT, title, message, productId);
    }

    public void notifyFollowersNewContent(UUID creatorId, UUID postId, String caption) {
        String name = creatorDisplayName(creatorId);
        String title = "New content";
        String snippet = caption != null && !caption.isBlank() ? caption.trim() : "a new post";
        if (snippet.length() > 80) {
            snippet = snippet.substring(0, 77) + "…";
        }
        String message = name + " shared new content: " + snippet;
        fanOut(creatorId, TYPE_NEW_CONTENT, title, message, postId);
    }

    public void notifyFollowersNewService(UUID creatorId, UUID serviceId, String serviceTitle) {
        String name = creatorDisplayName(creatorId);
        String title = "New service";
        String item = serviceTitle != null && !serviceTitle.isBlank() ? serviceTitle.trim() : "Untitled";
        String message = name + " added a new service: " + item;
        fanOut(creatorId, TYPE_NEW_SERVICE, title, message, serviceId);
    }

    private void fanOut(UUID creatorId, String type, String title, String message, UUID refId) {
        if (creatorId == null || refId == null) {
            return;
        }
        List<UUID> followerIds = creatorFollowRepository.findFollowerIdsByCreatorId(creatorId);
        if (followerIds.isEmpty()) {
            log.info("Follower notify type={} creator={} ref={} recipients=0 (no followers)",
                    type, creatorId, refId);
            return;
        }
        int sent = 0;
        for (UUID followerId : followerIds) {
            if (followerId == null || followerId.equals(creatorId)) {
                continue;
            }
            try {
                notificationService.createAndSend(
                        followerId, type, title, message, "PLATFORM", refId, creatorId);
                sent++;
            } catch (Exception e) {
                log.warn("Follower notify failed creator={} follower={} type={}: {}",
                        creatorId, followerId, type, e.getMessage());
            }
        }
        log.info("Follower notify type={} creator={} ref={} recipients={}", type, creatorId, refId, sent);
    }

    private String creatorDisplayName(UUID creatorId) {
        return userRepository.findByIdAndDeletedAtIsNull(creatorId)
                .map(User::getFullName)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .orElse("A creator you follow");
    }
}
