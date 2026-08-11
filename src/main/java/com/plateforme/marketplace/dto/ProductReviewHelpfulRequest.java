package com.plateforme.marketplace.dto;

import jakarta.validation.constraints.NotNull;

public record ProductReviewHelpfulRequest(@NotNull Boolean helpful) {}
