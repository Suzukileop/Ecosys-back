package com.plateforme.auth.dto;

/** Same shape whether or not the address has an account, so the endpoint reveals nothing. */
public record ResendVerificationResponse(long resendCooldownSeconds) {}
