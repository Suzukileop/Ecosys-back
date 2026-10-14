package com.plateforme.marketplace.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** A saved post filed in a collection. */
@Entity
@Table(name = "saved_collection_items",
        uniqueConstraints = @UniqueConstraint(name = "uq_saved_collection_items", columnNames = {"collection_id", "post_id"}))
@Getter
@Setter
@NoArgsConstructor
public class SavedCollectionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    private SavedCollection collection;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    @PrePersist
    protected void onCreate() {
        addedAt = LocalDateTime.now();
    }
}
