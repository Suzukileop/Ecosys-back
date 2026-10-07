package com.plateforme.marketplace.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/** A post a viewer asked to stop seeing in their own feed ("Not interested"). */
@Entity
@Table(name = "content_post_hides")
@IdClass(ContentPostHide.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class ContentPostHide {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ContentPostHide(UUID userId, UUID postId) {
        this.userId = userId;
        this.postId = postId;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID userId;
        private UUID postId;
    }
}
