package com.plateforme.user.dto;

import java.util.UUID;

/** Proof / portfolio link attached to an experience block (GitHub, Facebook, etc.). */
public record ExperienceProofLink(
        UUID id,
        String label,
        String url,
        String platform,
        int sortOrder
) {
}
