package com.plateforme.scheduler.entity;

import com.plateforme.ecosystem.entity.NicheRequest;
import com.plateforme.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "scheduled_posts")
@Getter
@Setter
@NoArgsConstructor
@Slf4j
public class ScheduledPost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id")
    private ScheduledConfig scheduledConfig;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "niche_request_id")
    private NicheRequest nicheRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Platform platform;

    @Column(name = "content_url", length = 500)
    private String contentUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 20)
    private ContentType contentType = ContentType.EXTERNAL_URL;

    @Column(columnDefinition = "TEXT")
    private String caption;

    @Column(name = "niche_ref", length = 20)
    private String nicheRef;

    /** Numéro séquentiel de livraison agent pour cette niche (1, 2, 3…). */
    @Column(name = "delivery_number")
    private Integer deliveryNumber;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status = PostStatus.SCHEDULED;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
