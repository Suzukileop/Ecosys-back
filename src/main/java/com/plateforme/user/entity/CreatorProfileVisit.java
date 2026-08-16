package com.plateforme.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "creator_profile_visits",
        uniqueConstraints = @UniqueConstraint(columnNames = {"creator_user_id", "visitor_key"})
)
@Getter
@Setter
@NoArgsConstructor
public class CreatorProfileVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "creator_user_id", nullable = false)
    private UUID creatorUserId;

    @Column(name = "viewer_user_id")
    private UUID viewerUserId;

    @Column(name = "visitor_key", nullable = false, length = 120)
    private String visitorKey;

    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;

    /** How many times this visitor opened the profile (unique row still one per visitor_key). */
    @Column(name = "visit_count", nullable = false)
    private Integer visitCount = 1;

    @PrePersist
    protected void onCreate() {
        if (viewedAt == null) {
            viewedAt = LocalDateTime.now();
        }
        if (visitCount == null || visitCount < 1) {
            visitCount = 1;
        }
    }
}
