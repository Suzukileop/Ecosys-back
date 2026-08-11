package com.plateforme.shared.cache;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.regex.Pattern;

/**
 * Adds Cache-Control response headers on public (unauthenticated) GETs.
 *
 * Rules (applied in order, first match wins):
 *
 *  • /api/marketplace/products/{id}/reviews/summary → public, 2 min
 *  • /api/marketplace/products/{id}/reviews         → public, 1 min
 *  • /api/marketplace/products/{id}/similar         → public, 5 min
 *  • /api/marketplace/products/{id}                 → public, 1 min
 *  • /api/marketplace/products (list)               → public, 30 s
 *  • any authenticated endpoint (contains /me, /ownership, /purchase…) → private, no-store
 *  • everything else under /api/marketplace          → public, 30 s
 *
 * Authenticated requests (Bearer present) skip public caching and get private headers.
 */
public class HttpCacheInterceptor implements HandlerInterceptor {

    private static final Pattern REVIEWS_SUMMARY =
            Pattern.compile("^/api/marketplace/products/[^/]+/reviews/summary$");
    private static final Pattern REVIEWS_LIST =
            Pattern.compile("^/api/marketplace/products/[^/]+/reviews$");
    private static final Pattern SIMILAR =
            Pattern.compile("^/api/marketplace/products/[^/]+/similar$");
    private static final Pattern PRODUCT_DETAIL =
            Pattern.compile("^/api/marketplace/products/[^/]+$");
    private static final Pattern PRODUCT_LIST =
            Pattern.compile("^/api/marketplace/products$");

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {

        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();

        // Per-user endpoints → always private
        if (isUserSpecific(path) || hasBearer(request)) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
            return true;
        }

        // Public GETs — apply graduated TTLs
        if (REVIEWS_SUMMARY.matcher(path).matches()) {
            setPublicCache(response, 120, 600);   // 2 min fresh, 10 min stale
        } else if (REVIEWS_LIST.matcher(path).matches()) {
            setPublicCache(response, 60, 300);    // 1 min fresh, 5 min stale
        } else if (SIMILAR.matcher(path).matches()) {
            setPublicCache(response, 300, 900);   // 5 min fresh, 15 min stale
        } else if (PRODUCT_DETAIL.matcher(path).matches()) {
            setPublicCache(response, 60, 300);    // 1 min fresh, 5 min stale
        } else if (PRODUCT_LIST.matcher(path).matches()) {
            setPublicCache(response, 30, 120);    // 30 s fresh, 2 min stale
        } else if (path.startsWith("/api/marketplace")) {
            setPublicCache(response, 30, 120);
        }

        return true;
    }

    private static void setPublicCache(HttpServletResponse response, int maxAge, int staleWhileRevalidate) {
        response.setHeader(HttpHeaders.CACHE_CONTROL,
                "public, max-age=" + maxAge + ", stale-while-revalidate=" + staleWhileRevalidate);
    }

    private static boolean isUserSpecific(String path) {
        return path.contains("/me")
                || path.contains("/ownership")
                || path.contains("/purchases/")
                || path.contains("/reactions/me")
                || path.contains("/favorites/me");
    }

    private static boolean hasBearer(HttpServletRequest request) {
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        return auth != null && auth.startsWith("Bearer ");
    }
}
