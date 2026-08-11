package com.plateforme.ecosystem.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record NicheRequestResponse(
        UUID id,
        String uniqueCode,
        String nicheTheme,
        String description,
        String language,
        int nbPostsPerWeek,
        List<String> platforms,
        String refType,
        String refMctCode,
        String refExternalUrl,
        String refFileUrl,
        Integer monthlyAmountCents,
        String monthlyAmountFormatted,
        String status,
        String paymentStatus,
        boolean botConfirmed,
        String demoContentUrl,
        String agentNotes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime activatedAt,
        String nextStep,
        String checkoutUrl,
        String rejectionReason,
        UUID agentId,
        String clientEmail,
        String clientFullName
) {
    public NicheRequestResponse withCheckoutUrl(String url) {
        return new NicheRequestResponse(
                id, uniqueCode, nicheTheme, description, language, nbPostsPerWeek, platforms,
                refType, refMctCode, refExternalUrl, refFileUrl, monthlyAmountCents, monthlyAmountFormatted,
                status, paymentStatus, botConfirmed, demoContentUrl, agentNotes, createdAt, updatedAt, activatedAt,
                nextStep, url, rejectionReason, agentId, clientEmail, clientFullName);
    }
}
