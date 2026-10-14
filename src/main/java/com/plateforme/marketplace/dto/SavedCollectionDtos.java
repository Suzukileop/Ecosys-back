package com.plateforme.marketplace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Payloads of Settings > Saved posts (collections of saved posts). */
public final class SavedCollectionDtos {

    private SavedCollectionDtos() {
    }

    /** One tile of a cover mosaic: the post's cover media and its type (FILE / GIF). */
    public record Cover(String url, String mediaType) {
    }

    public record CollectionResponse(
            UUID id,
            String name,
            long itemCount,
            List<Cover> covers,
            LocalDateTime updatedAt
    ) {
    }

    /** Everything the Saved page shows on landing: the "All saved" tile, then every collection. */
    public record OverviewResponse(
            long allSavedCount,
            List<Cover> allSavedCovers,
            List<CollectionResponse> collections
    ) {
    }

    /** Create (optionally filing a post straight away) or rename. */
    public record NameRequest(
            @NotBlank @Size(max = 60) String name,
            UUID postId
    ) {
    }
}
