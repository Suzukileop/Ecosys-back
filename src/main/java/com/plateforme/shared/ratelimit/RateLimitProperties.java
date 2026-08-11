package com.plateforme.shared.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rate-limit capacities (requests per minute per key) loaded from application.yml.
 */
@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(
        int marketplacePublicRead,
        int marketplaceAuthRead,
        int marketplaceWrite,
        int authRefresh,
        int authLogin,
        int authSignup
) {
    public RateLimitProperties {
        if (marketplacePublicRead <= 0) marketplacePublicRead = 300;
        if (marketplaceAuthRead <= 0)   marketplaceAuthRead = 120;
        if (marketplaceWrite <= 0)      marketplaceWrite = 30;
        if (authRefresh <= 0)           authRefresh = 30;
        if (authLogin <= 0)             authLogin = 5;
        if (authSignup <= 0)            authSignup = 3;
    }
}
