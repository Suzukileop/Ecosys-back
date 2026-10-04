package com.plateforme.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_settings")
@Getter
@Setter
@NoArgsConstructor
public class UserSettings {

    public enum MessagePermission { EVERYONE, FOLLOWING, NOBODY }

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "email_notifications", nullable = false)
    private Boolean emailNotifications = true;

    @Column(name = "notify_messages", nullable = false)
    private Boolean notifyMessages = true;

    @Column(name = "notify_comments", nullable = false)
    private Boolean notifyComments = true;

    @Column(name = "notify_followers", nullable = false)
    private Boolean notifyFollowers = true;

    @Column(name = "notify_profile_visits", nullable = false)
    private Boolean notifyProfileVisits = true;

    @Column(name = "notify_sales", nullable = false)
    private Boolean notifySales = true;

    @Column(name = "notify_following_activity", nullable = false)
    private Boolean notifyFollowingActivity = true;

    @Column(name = "show_online_status", nullable = false)
    private Boolean showOnlineStatus = true;

    @Column(name = "private_profile_views", nullable = false)
    private Boolean privateProfileViews = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_permission", nullable = false, length = 20)
    private MessagePermission messagePermission = MessagePermission.EVERYONE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public UserSettings(UUID userId) {
        this.userId = userId;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
