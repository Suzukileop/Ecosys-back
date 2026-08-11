package com.plateforme.scheduler.entity;

import com.plateforme.ecosystem.entity.NicheRequest;
import com.plateforme.scheduler.dto.PublicationSlotDto;
import com.plateforme.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "scheduled_configs")
@Getter
@Setter
@NoArgsConstructor
@Slf4j
public class ScheduledConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "niche_request_id", nullable = false, unique = true)
    private NicheRequest nicheRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "publication_slots", nullable = false, columnDefinition = "jsonb")
    private List<PublicationSlotDto> publicationSlots = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> platforms = new ArrayList<>();

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (publicationSlots == null) {
            publicationSlots = new ArrayList<>();
        }
        if (platforms == null) {
            platforms = new ArrayList<>();
        }
        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
