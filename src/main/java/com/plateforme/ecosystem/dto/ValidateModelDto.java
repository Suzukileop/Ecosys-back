package com.plateforme.ecosystem.dto;

import jakarta.validation.constraints.Size;

public record ValidateModelDto(
        boolean accepted,
        @Size(max = 500) String rejectionReason
) {
}
