package com.plateforme.admin.dto;

public record AdminGlobalStatsResponse(
        long totalUsers,
        long totalPublishedPosts,
        long totalCreators,
        long totalRevenueCredits
) {}
