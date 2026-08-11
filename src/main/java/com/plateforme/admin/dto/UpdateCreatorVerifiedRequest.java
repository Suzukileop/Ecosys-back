package com.plateforme.admin.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateCreatorVerifiedRequest(
        @NotNull
        Boolean verified
) {}
