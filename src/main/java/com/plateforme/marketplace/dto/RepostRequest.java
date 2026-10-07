package com.plateforme.marketplace.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RepostRequest(
        @NotNull UUID postId,
        /** Optional note shown above the original ("repost with your thoughts"). */
        @Size(max = 3000) String comment
) {
}
