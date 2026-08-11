package com.plateforme.credits.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SetCreditBalanceRequest(
        @NotNull @Min(0) Integer balance
) {
}
