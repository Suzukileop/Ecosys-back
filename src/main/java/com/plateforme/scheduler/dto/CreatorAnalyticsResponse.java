package com.plateforme.scheduler.dto;

public record CreatorAnalyticsResponse(
        long totalContentPosts,
        long totalViews,
        long totalLikes
) {}
