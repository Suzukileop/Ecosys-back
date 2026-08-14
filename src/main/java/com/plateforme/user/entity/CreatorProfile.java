package com.plateforme.user.entity;

import com.plateforme.user.dto.ContactVisibilitySettings;
import com.plateforme.user.dto.FaqItemDto;
import com.plateforme.user.dto.ProfileContactEntryDto;
import com.plateforme.user.dto.ProfileGalleryItemDto;
import com.plateforme.user.dto.ProfileLinkDto;
import com.plateforme.user.dto.ProfileMediaBlock;
import com.plateforme.user.dto.ProfileServiceDto;
import com.plateforme.user.dto.ProfileStrengthToolDto;
import com.plateforme.user.dto.ProfileTeamMemberDto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "creator_profiles")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class CreatorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(length = 150)
    private String specialite;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(name = "is_verified")
    private Boolean isVerified = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "social_links", columnDefinition = "jsonb")
    private String socialLinks;

    @Column(name = "location_city", length = 150)
    private String locationCity;

    @Column(name = "location_country", length = 100)
    private String locationCountry;

    @Column(name = "location_lat")
    private Double locationLat;

    @Column(name = "location_lng")
    private Double locationLng;

    @Column(name = "timezone_id", length = 80)
    private String timezoneId;

    @Column(length = 255)
    private String languages;

    @Column(length = 50)
    private String gender;

    /** App experience role: GENERAL_MEMBER (default), SERVICE_PROVIDER, FREELANCER_STUDENT, JOB_SEEKER, RH_RECRUITER. */
    @Column(name = "app_role", nullable = false, length = 40)
    private String appRole = "GENERAL_MEMBER";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "spoken_languages", columnDefinition = "jsonb", nullable = false)
    private List<String> spokenLanguages = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile_services", columnDefinition = "jsonb", nullable = false)
    private List<ProfileServiceDto> profileServices = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "faq_items", columnDefinition = "jsonb", nullable = false)
    private List<FaqItemDto> faqItems = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "team_members", columnDefinition = "jsonb", nullable = false)
    private List<ProfileTeamMemberDto> teamMembers = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gallery_items", columnDefinition = "jsonb", nullable = false)
    private List<ProfileGalleryItemDto> galleryItems = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile_links", columnDefinition = "jsonb", nullable = false)
    private List<ProfileLinkDto> profileLinks = new ArrayList<>();

    @Column(name = "avg_response_time_seconds")
    private Integer avgResponseTimeSeconds;

    @Column(name = "response_time_sample_count", nullable = false)
    private Integer responseTimeSampleCount = 0;

    @Column(name = "response_time_computed_at")
    private LocalDateTime responseTimeComputedAt;

    @Column(name = "typical_response_time", length = 40)
    private String typicalResponseTime;

    @Column(name = "cta_label", length = 100)
    private String ctaLabel;

    @Column(name = "cta_url", length = 500)
    private String ctaUrl;

    @Column(name = "contact_address", length = 300)
    private String contactAddress;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contact_addresses", columnDefinition = "jsonb", nullable = false)
    private List<ProfileContactEntryDto> contactAddresses = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contact_phones", columnDefinition = "jsonb", nullable = false)
    private List<ProfileContactEntryDto> contactPhones = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contact_emails", columnDefinition = "jsonb", nullable = false)
    private List<ProfileContactEntryDto> contactEmails = new ArrayList<>();

    @Column(name = "availability_hours", length = 200)
    private String availabilityHours;

    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contact_visibility", columnDefinition = "jsonb", nullable = false)
    private String contactVisibility = ContactVisibilitySettings.defaultJson();

    @Column(name = "studio_header_layout", nullable = false, length = 20)
    private String studioHeaderLayout = "BANNER";

    @Column(name = "studio_header_content_style", nullable = false, length = 20)
    private String studioHeaderContentStyle = "DEFAULT";

    @Column(name = "studio_tab_nav_align", nullable = false, length = 10)
    private String studioTabNavAlign = "LEFT";

    /** Custom Content tab headline in Creator Studio (empty/null = default). */
    @Column(name = "studio_content_headline", length = 160)
    private String studioContentHeadline;

    /** Public boutique / shop name for all marketplace products (Explore search + display). */
    @Column(name = "shop_name", length = 120)
    private String shopName;

    /** Short answer to "What do you sell?" shown on the public shop. */
    @Column(name = "shop_selling_focus", length = 200)
    private String shopSellingFocus;

    /** Public shop description. */
    @Column(name = "shop_description", length = 2000)
    private String shopDescription;

    /** Public shop cover media URL (image or short video). */
    @Column(name = "shop_cover_url", length = 1000)
    private String shopCoverUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "why_me_blocks", columnDefinition = "jsonb", nullable = false)
    private List<ProfileMediaBlock> whyMeBlocks = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "experience_blocks", columnDefinition = "jsonb", nullable = false)
    private List<ProfileMediaBlock> experienceBlocks = new ArrayList<>();

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "strengths_tools_mastered", columnDefinition = "jsonb", nullable = false)
    private List<ProfileStrengthToolDto> strengthsToolsMastered = new ArrayList<>();

    @Column(name = "profile_visits", nullable = false)
    private Integer profileVisits = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "portfolio_settings", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> portfolioSettings = new HashMap<>();

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
