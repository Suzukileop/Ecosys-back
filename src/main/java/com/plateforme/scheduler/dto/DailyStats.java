package com.plateforme.scheduler.dto;

import java.time.LocalDate;

public record DailyStats(
        LocalDate date,
        int count,
        int views,
        int likes
) {}
