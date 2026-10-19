package com.plateforme.auth.security;

import com.plateforme.auth.dto.AuthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Hands the refresh token to the browser as an HttpOnly cookie, never in the JSON body.
 *
 * <p>The token used to travel in the response body, then through JavaScript (and localStorage) to a
 * Next route that turned it into a cookie: any XSS could read a 7-day session. The API now sets the
 * cookie itself. Browsers ignore the port for cookies, so a cookie set by {@code localhost:8080} is
 * the same one the Next proxy reads on {@code localhost:3000}; in production both sit behind one
 * origin (the {@code /api} rewrite).
 */
@Component
public class RefreshTokenCookies {

    public static final String NAME = "refresh_token";

    private final Duration maxAge;
    private final boolean secure;

    public RefreshTokenCookies(
            @Value("${app.jwt.refresh-expiration}") long refreshExpirationMs,
            @Value("${app.cookie.secure:}") String secureSetting,
            @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.maxAge = Duration.ofMillis(refreshExpirationMs);
        /* Unset: Secure whenever the site is served over HTTPS. An http:// LAN address would drop a
           Secure cookie, so local and LAN setups keep working without configuration. */
        this.secure = secureSetting == null || secureSetting.isBlank()
                ? frontendUrl != null && frontendUrl.trim().startsWith("https://")
                : Boolean.parseBoolean(secureSetting.trim());
    }

    /** 200 with the session cookie set and the refresh token removed from the body. */
    public ResponseEntity<AuthResponse> issue(AuthResponse response) {
        if (response.refreshToken() == null || response.refreshToken().isBlank()) {
            return ResponseEntity.ok(response);
        }
        ResponseCookie cookie = base(response.refreshToken()).maxAge(maxAge).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response.withoutRefreshToken());
    }

    /** Set-Cookie value that deletes the session cookie (same attributes, Max-Age=0). */
    public String clearHeader() {
        return base("").maxAge(Duration.ZERO).build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/");
    }
}
