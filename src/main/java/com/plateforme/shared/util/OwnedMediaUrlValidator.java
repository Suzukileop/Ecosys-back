package com.plateforme.shared.util;

import com.plateforme.shared.exception.BusinessException;

import java.util.UUID;

public final class OwnedMediaUrlValidator {

    private OwnedMediaUrlValidator() {}

    public static void validate(String mediaUrl, UUID userId) {
        if (!mediaUrl.startsWith("http://") && !mediaUrl.startsWith("https://")) {
            throw new BusinessException("BLOCK_MEDIA_URL_INVALID", "Media URL must use http or https.");
        }
        String userSegment = userId.toString();
        if (!mediaUrl.contains("/content/public/" + userSegment + "/")
                && !mediaUrl.contains("content/public/" + userSegment + "/")
                && !mediaUrl.contains("/marketplace/public/" + userSegment + "/")
                && !mediaUrl.contains("marketplace/public/" + userSegment + "/")) {
            throw new BusinessException("BLOCK_MEDIA_URL_FORBIDDEN",
                    "Media URL must belong to your uploaded content.");
        }
    }
}
