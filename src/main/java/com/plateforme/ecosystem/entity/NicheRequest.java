package com.plateforme.ecosystem.entity;

import com.plateforme.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "niche_requests")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@Slf4j
public class NicheRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private User agent;

    @Column(name = "niche_theme", nullable = false, length = 200)
    private String nicheTheme;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 10)
    private String language = "FR";

    @Column(name = "nb_posts_per_week", nullable = false)
    private Short nbPostsPerWeek;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> platforms = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", length = 10)
    private RefType refType;

    @Column(name = "ref_mct_code", length = 20)
    private String refMctCode;

    @Column(name = "ref_external_url", length = 500)
    private String refExternalUrl;

    @Column(name = "ref_file_url", length = 500)
    private String refFileUrl;

    @Column(name = "monthly_amount_cents")
    private Integer monthlyAmountCents;

    @Column(name = "unique_code", unique = true, length = 20)
    private String uniqueCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NicheStatus status = NicheStatus.PENDING;

    @Column(name = "bot_confirmed")
    private Boolean botConfirmed = false;

    @Column(name = "bot_confirmed_at")
    private LocalDateTime botConfirmedAt;

    @Column(name = "demo_content_url", length = 500)
    private String demoContentUrl;

    /** Vidéo modèle réutilisable (pipeline IA) — distincte de demo_content_url */
    @Column(name = "model_video_url", length = 500)
    private String modelVideoUrl;

    @Column(name = "agent_notes", columnDefinition = "TEXT")
    private String agentNotes;

    @Column(name = "proposed_at")
    private LocalDateTime proposedAt;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(name = "vpi_payment_id", length = 300)
    private String vpiPaymentId;

    @Column(name = "vpi_reference", length = 300)
    private String vpiReference;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    private LocalDateTime deadline;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (platforms == null) {
            platforms = new ArrayList<>();
        }
        if (botConfirmed == null) {
            botConfirmed = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
