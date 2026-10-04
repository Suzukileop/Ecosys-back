package com.plateforme.user.dto;

public record UserSettingsDto(
        boolean emailNotifications,
        boolean notifyMessages,
        boolean notifyComments,
        boolean notifyFollowers,
        boolean notifyProfileVisits,
        boolean notifySales,
        boolean notifyFollowingActivity,
        boolean showOnlineStatus,
        boolean privateProfileViews,
        String messagePermission
) {}
