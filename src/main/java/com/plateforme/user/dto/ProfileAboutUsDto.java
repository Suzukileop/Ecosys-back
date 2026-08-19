package com.plateforme.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProfileAboutUsDto(
        @Size(max = 150) String title,
        @Size(max = 4000) String description,
        @Size(max = 12) List<String> tasks,
        @Size(max = 2) List<String> imageUrls,
        @Size(max = 500) String quote,
        @Valid ProfileAboutUsFounderDto founder
) {}
