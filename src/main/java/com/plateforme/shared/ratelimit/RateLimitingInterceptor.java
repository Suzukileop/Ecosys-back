package com.plateforme.shared.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Three-tier rate limiter for marketplace routes:
 *
 *  • PUBLIC_READ   (GET, no user-specific data) — high limit, keyed by IP
 *  • AUTH_READ     (GET returning per-user data) — medium limit, keyed by userId when available
 *  • WRITE         (POST / DELETE mutating data) — strict limit, keyed by userId when available
 *
 * Auth routes keep their own independent buckets.
 *
 * All limits are configurable via application.yml (rate-limit.*) and default to safe
 * values so the app starts even without explicit config.
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimitingInterceptor implements HandlerInterceptor {

    private final RateLimitProperties props;

    // ── Auth buckets (keyed by IP) ──────────────────────────────────────────
    private final Map<String, Bucket> loginBuckets    = new ConcurrentHashMap<>();
    private final Map<String, Bucket> signupBuckets   = new ConcurrentHashMap<>();
    private final Map<String, Bucket> refreshBuckets  = new ConcurrentHashMap<>();

    // ── Marketplace buckets (keyed by userId or IP) ─────────────────────────
    private final Map<String, Bucket> mktPublicReadBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> mktAuthReadBuckets   = new ConcurrentHashMap<>();
    private final Map<String, Bucket> mktWriteBuckets      = new ConcurrentHashMap<>();

    // ────────────────────────────────────────────────────────────────────────

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String path     = request.getRequestURI();
        String method   = request.getMethod();
        String clientIp = getClientIp(request);

        BucketEntry entry = resolveBucket(path, method, clientIp);
        if (entry == null) {
            return true;
        }

        ConsumptionProbe probe = entry.bucket().tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return true;
        }

        long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
        log.warn("Rate limit dépassé pour key={} sur {} {}", entry.key(), method, path);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.getWriter().write(
                "{\"status\":429,\"error\":\"Too Many Requests\"," +
                "\"message\":\"Too many requests. Please try again in " + retryAfterSeconds + " seconds.\","+
                "\"retryAfterSeconds\":" + retryAfterSeconds + "}");
        return false;
    }

    // ── Bucket resolution ────────────────────────────────────────────────────

    private BucketEntry resolveBucket(String path, String method, String clientIp) {
        // Auth routes — always keyed by IP
        if (path.equals("/api/auth/login")) {
            return new BucketEntry(clientIp,
                    loginBuckets.computeIfAbsent(clientIp, k -> bucket(props.authLogin())));
        }
        if (path.equals("/api/auth/signup")) {
            return new BucketEntry(clientIp,
                    signupBuckets.computeIfAbsent(clientIp, k -> bucket(props.authSignup())));
        }
        if (path.equals("/api/auth/refresh")) {
            return new BucketEntry(clientIp,
                    refreshBuckets.computeIfAbsent(clientIp, k -> bucket(props.authRefresh())));
        }

        // Marketplace routes — three-tier classification
        if (path.startsWith("/api/marketplace")) {
            MarketplaceTier tier = classifyMarketplace(path, method);
            String userKey = resolveUserKey(clientIp);

            return switch (tier) {
                case PUBLIC_READ -> new BucketEntry(clientIp,
                        mktPublicReadBuckets.computeIfAbsent(clientIp,
                                k -> bucket(props.marketplacePublicRead())));
                case AUTH_READ -> new BucketEntry(userKey,
                        mktAuthReadBuckets.computeIfAbsent(userKey,
                                k -> bucket(props.marketplaceAuthRead())));
                case WRITE -> new BucketEntry(userKey,
                        mktWriteBuckets.computeIfAbsent(userKey,
                                k -> bucket(props.marketplaceWrite())));
            };
        }

        return null;
    }

    /**
     * Three-tier classification for /api/marketplace/** requests.
     *
     * PUBLIC_READ  — unauthenticated-friendly GETs (catalog, reviews list, similar products…)
     * AUTH_READ    — GETs that return per-user data (ownership, my review, reaction counts with userId…)
     * WRITE        — mutating verbs (POST / DELETE / PUT / PATCH)
     */
    private MarketplaceTier classifyMarketplace(String path, String method) {
        boolean isGet = "GET".equalsIgnoreCase(method);

        if (!isGet) {
            return MarketplaceTier.WRITE;
        }

        // Endpoints that return user-specific data belong to AUTH_READ
        if (path.contains("/reviews/me")
                || path.contains("/ownership")
                || path.contains("/purchases/")
                || path.contains("/favorites/me")
                || path.contains("/reactions/me")) {
            return MarketplaceTier.AUTH_READ;
        }

        return MarketplaceTier.PUBLIC_READ;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns the authenticated user's ID as bucket key when available,
     * falls back to IP to avoid NAT-shared limits on public GETs.
     */
    private String resolveUserKey(String clientIp) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails ud) {
            return "user:" + ud.getUsername();
        }
        return "ip:" + clientIp;
    }

    /**
     * Greedy refill: tokens are added continuously rather than all at once at
     * the end of the minute.  For capacityPerMinute=120 this means 1 new token
     * every 500 ms, so short bursts are absorbed without blocking the client for
     * up to 60 seconds as would happen with refillIntervally.
     */
    private static Bucket bucket(int capacityPerMinute) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacityPerMinute)
                .refillGreedy(capacityPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) {
            return xri;
        }
        return request.getRemoteAddr();
    }

    private enum MarketplaceTier { PUBLIC_READ, AUTH_READ, WRITE }

    private record BucketEntry(String key, Bucket bucket) {}
}
