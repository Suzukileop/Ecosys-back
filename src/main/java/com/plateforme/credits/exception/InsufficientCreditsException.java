package com.plateforme.credits.exception;

import lombok.Getter;

@Getter
public class InsufficientCreditsException extends RuntimeException {

    private final int required;
    private final int available;

    public InsufficientCreditsException(int required, int available) {
        super("Crédits insuffisants : requis=" + required + ", disponible=" + available);
        this.required = required;
        this.available = available;
    }
}
