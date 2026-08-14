package com.plateforme.marketplace.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "marketplace_product_group_items")
@Getter
@Setter
@NoArgsConstructor
public class MarketplaceProductGroupItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private MarketplaceProductGroup productGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private MarketplaceProduct product;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
