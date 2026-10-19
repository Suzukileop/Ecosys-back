package com.plateforme.shared.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The caller's IP for rate limiting and view counting.
 *
 * <p>Reading {@code X-Forwarded-For} by hand trusted whatever the client sent: anyone could pick a
 * new "IP" per request and walk around the login limit. With {@code server.forward-headers-strategy:
 * native}, Tomcat's RemoteIpValve rewrites {@code getRemoteAddr()} from that header only when the
 * request comes from an internal proxy (loopback / private ranges — the Next server, a tunnel or a
 * load balancer), so the value below is the real client behind a trusted hop and the socket peer
 * otherwise.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String resolve(HttpServletRequest request) {
        String addr = request.getRemoteAddr();
        return addr == null || addr.isBlank() ? "unknown" : addr;
    }
}
