package com.plateforme.auth.dto;

/**
 * Sign-up no longer opens a session: the account stays locked until the e-mail code is confirmed
 * (POST /api/auth/verify-email), which then returns the tokens.
 */
public record SignupResponse(
        String email,
        boolean verificationRequired,
        long resendCooldownSeconds,
        int codeTtlMinutes
) {}
