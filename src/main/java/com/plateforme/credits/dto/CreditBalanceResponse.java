package com.plateforme.credits.dto;

import java.util.UUID;

public record CreditBalanceResponse(
        UUID userId,
        String email,
        int balance
) {
}
