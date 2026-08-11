package com.plateforme.marketplace.dto;

public record ProductPreviewResponse(
        String previewUrl,
        Integer previewLimitPercent,
        String demoDescription
) {}
