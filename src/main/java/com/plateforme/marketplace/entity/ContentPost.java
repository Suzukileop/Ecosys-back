package com.plateforme.marketplace.entity;

import com.plateforme.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "content_posts")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class ContentPost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(length = 300)
    private String title;

    @Column(length = 100)
    private String genre;

    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    @Column(name = "media_type", nullable = false, length = 20)
    private String mediaType = "FILE";

    @Column(name = "text_color", length = 20)
    private String textColor;

    @Column(name = "mood_label", length = 100)
    private String moodLabel;

    @Column(name = "mood_emoji", length = 20)
    private String moodEmoji;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tagged_user_ids", columnDefinition = "jsonb", nullable = false)
    private List<UUID> taggedUserIds = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_info", length = 200)
    private String priceInfo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tools_used", columnDefinition = "jsonb")
    private List<String> toolsUsed = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<String> tags = new ArrayList<>();

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = true;

    @Column(name = "comments_enabled", nullable = false)
    private Boolean commentsEnabled = true;

    @Column(nullable = false)
    private Integer views = 0;

    @Column(nullable = false)
    private Integer likes = 0;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "pinned_at")
    private LocalDateTime pinnedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (toolsUsed == null) {
            toolsUsed = new ArrayList<>();
        }
        if (tags == null) {
            tags = new ArrayList<>();
        }
        if (taggedUserIds == null) {
            taggedUserIds = new ArrayList<>();
        }
        if (mediaType == null || mediaType.isBlank()) {
            mediaType = "FILE";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
