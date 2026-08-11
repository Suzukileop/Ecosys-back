package com.plateforme.marketplace.dto;

import com.plateforme.marketplace.entity.AccessMode;

public record AccessUrlResponse(
        String url,
        AccessMode accessMode,
        int expiresInMinutes,
        String filename
) {}
