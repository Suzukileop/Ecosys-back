package com.plateforme.marketplace.entity;

import com.plateforme.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "marketplace_purchases")
@Getter
@Setter
@NoArgsConstructor
public class MarketplacePurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private MarketplaceProduct product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bundle_id")
    private MarketplaceBundle bundle;

    @Column(name = "price_paid_cents", nullable = false)
    private Integer pricePaidCents;

    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private MarketplacePurchaseStatus paymentStatus = MarketplacePurchaseStatus.COMPLETED;

    @Column(name = "download_count", nullable = false)
    private Integer downloadCount = 0;

    @Column(name = "purchased_at", nullable = false)
    private LocalDateTime purchasedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        if (purchasedAt == null) {
            purchasedAt = LocalDateTime.now();
        }
    }
}
