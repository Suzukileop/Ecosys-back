package com.plateforme.marketplace.entity;

import com.plateforme.marketplace.dto.ProductWhyBlock;
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
@Table(name = "marketplace_products")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class MarketplaceProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductType type;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_cents", nullable = false)
    private Integer priceCents;

    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @Column(length = 100)
    private String genre;

    @Column(length = 150)
    private String specialite;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "demo_type", nullable = false, length = 20)
    private DemoType demoType = DemoType.NONE;

    @Column(name = "demo_url", length = 500)
    private String demoUrl;

    @Column(name = "demo_description", columnDefinition = "TEXT")
    private String demoDescription;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "demo_subtitles", columnDefinition = "jsonb", nullable = false)
    private List<String> demoSubtitles = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "why_product_blocks", columnDefinition = "jsonb", nullable = false)
    private List<ProductWhyBlock> whyProductBlocks = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_mode", nullable = false, length = 20)
    private DeliveryMode deliveryMode = DeliveryMode.BOTH;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "compatible_tools", columnDefinition = "jsonb")
    private List<String> compatibleTools = new ArrayList<>();

    @Column(name = "file_format", length = 50)
    private String fileFormat;

    @Column(name = "file_size_mb")
    private Integer fileSizeMb;

    @Column(length = 10)
    private String language;

    @Column(length = 20)
    private String version;

    @Column(name = "preview_limit_percent")
    private Integer previewLimitPercent;

    @Column(name = "max_downloads")
    private Integer maxDownloads;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> tags = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gallery_image_urls", columnDefinition = "jsonb", nullable = false)
    private List<String> galleryImageUrls = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bundle_id")
    private MarketplaceBundle bundle;

    @Column(nullable = false)
    private Integer views = 0;

    @Column(nullable = false)
    private Integer likes = 0;

    @Column(name = "sales_count", nullable = false)
    private Integer salesCount = 0;

    @Column(nullable = false)
    private Integer dislikes = 0;

    @Column(nullable = false)
    private Integer favorites = 0;

    @Column(nullable = false)
    private Integer comments = 0;

    @Column(nullable = false)
    private Integer downloads = 0;

    @Column(nullable = false)
    private Integer shares = 0;

    @Column(name = "is_published", nullable = false)
    private Boolean isPublished = true;

    @Column(name = "compare_at_price_cents")
    private Integer compareAtPriceCents;

    @Column(name = "video_duration_seconds")
    private Integer videoDurationSeconds;

    @Column(name = "video_resolution", length = 10)
    private String videoResolution;

    @Column(name = "is_bestseller", nullable = false)
    private Boolean isBestseller = false;

    /** When set, product is pinned to the top of the creator's listings. */
    @Column(name = "pinned_at")
    private LocalDateTime pinnedAt;

    @Column(name = "review_count", nullable = false)
    private Integer reviewCount = 0;

    @Column(name = "average_rating", columnDefinition = "numeric(3,2)")
    private Double averageRating;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (compatibleTools == null) {
            compatibleTools = new ArrayList<>();
        }
        if (tags == null) {
            tags = new ArrayList<>();
        }
        if (whyProductBlocks == null) {
            whyProductBlocks = new ArrayList<>();
        }
        if (demoSubtitles == null) {
            demoSubtitles = new ArrayList<>();
        }
        if (galleryImageUrls == null) {
            galleryImageUrls = new ArrayList<>();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
