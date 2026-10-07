package com.plateforme.auth.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Refuses to start a production instance that signs its tokens with the committed default key.
 *
 * <p>{@code app.jwt.secret} falls back to a literal that lives in this repository. Anyone holding
 * it can mint a token for any account, so it is only ever acceptable on a developer machine. The
 * check fails the boot on a production profile and warns loudly everywhere else, rather than
 * failing everywhere — a developer running {@code mvn spring-boot:run} with no profile set must
 * still get a working server.
 *
 * <p>The database password gets the same treatment: its fallback is committed too.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtSecretGuard {

    /** Must match the fallback in application.yml. */
    private static final String KNOWN_DEFAULT_SECRET = "dev-secret-key-minimum-256-bits-change-in-production";

    /** HMAC-SHA256 needs a 256-bit key; a shorter one is rejected by the JWT library anyway. */
    private static final int MIN_SECRET_BYTES = 32;

    private static final List<String> PRODUCTION_PROFILES = List.of("prod", "production", "staging");

    private final Environment environment;

    /** Must match the fallback in application.yml. */
    private static final String KNOWN_DEFAULT_DB_PASSWORD = "Pgsql_2025!Eco_Secure#X9";

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @PostConstruct
    void verify() {
        verifyDatabasePassword();
        String secret = jwtSecret == null ? "" : jwtSecret.trim();
        boolean isDefault = KNOWN_DEFAULT_SECRET.equals(secret);
        boolean tooShort = secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES;

        if (!isDefault && !tooShort) {
            return;
        }

        String reason = isDefault
                ? "app.jwt.secret is still the default committed in the repository"
                : "app.jwt.secret is shorter than " + MIN_SECRET_BYTES + " bytes";

        if (isProductionProfile()) {
            throw new IllegalStateException(
                    reason + ". Set the JWT_SECRET environment variable to a random value of at least "
                            + MIN_SECRET_BYTES + " bytes before starting a "
                            + Arrays.toString(environment.getActiveProfiles()) + " instance.");
        }
        log.warn("SECURITY: {} — anyone with this repository can forge a token for any account. "
                + "Set JWT_SECRET before exposing this instance (including through a tunnel).", reason);
    }

    private void verifyDatabasePassword() {
        if (!KNOWN_DEFAULT_DB_PASSWORD.equals(dbPassword)) {
            return;
        }
        String reason = "spring.datasource.password is still the default committed in the repository";
        if (isProductionProfile()) {
            throw new IllegalStateException(reason + ". Set the DB_PASSWORD environment variable before starting a "
                    + Arrays.toString(environment.getActiveProfiles()) + " instance.");
        }
        log.warn("SECURITY: {} — set DB_PASSWORD before deploying.", reason);
    }

    private boolean isProductionProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if (PRODUCTION_PROFILES.contains(profile.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
