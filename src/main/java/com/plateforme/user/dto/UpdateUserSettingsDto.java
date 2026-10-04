package com.plateforme.user.dto;

import jakarta.validation.constraints.Pattern;

/** Partial update — null fields are left unchanged. */
public record UpdateUserSettingsDto(
        Boolean emailNotifications,
        Boolean notifyMessages,
        Boolean notifyComments,
        Boolean notifyFollowers,
        Boolean notifyProfileVisits,
        Boolean notifySales,
        Boolean notifyFollowingActivity,
        Boolean showOnlineStatus,
        Boolean privateProfileViews,
        @Pattern(regexp = "^(EVERYONE|FOLLOWING|NOBODY)$", message = "Invalid message permission")
        String messagePermission
) {}
