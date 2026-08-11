package com.plateforme.auth.security;

import com.plateforme.user.entity.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;

/**
 * Extrait l'entité {@link User} du contexte sécurité ou des handlers WebSocket/STOMP.
 * <p>
 * En HTTP, {@link Authentication#getPrincipal()} est déjà le {@code User} domaine.
 * Sur WebSocket, le {@link Principal} injecté dans {@code @MessageMapping} est souvent
 * le {@link UsernamePasswordAuthenticationToken} lui‑même — il faut alors utiliser
 * {@link UsernamePasswordAuthenticationToken#getPrincipal()}.
 */
public final class CurrentUserUtil {

    private CurrentUserUtil() {
    }

    public static User requireUser(Principal principal) {
        User user = extractUser(principal);
        if (user == null) {
            throw new AccessDeniedException(
                    "Impossible de résoudre l'utilisateur — reconnectez-vous (Bearer JWT dans CONNECT WebSocket)");
        }
        return user;
    }

    public static User extractUser(Principal principal) {
        if (principal == null) {
            return null;
        }
        if (principal instanceof UsernamePasswordAuthenticationToken token) {
            Object p = token.getPrincipal();
            if (p instanceof User u) {
                return u;
            }
        }
        if (principal instanceof User u) {
            return u;
        }
        return null;
    }

    public static User requireUserFromAuthentication(Authentication authentication) {
        if (authentication == null) {
            throw new AccessDeniedException("Non authentifié");
        }
        Object p = authentication.getPrincipal();
        if (p instanceof User u) {
            return u;
        }
        throw new AccessDeniedException("Type de principal non supporté: " + p.getClass().getName());
    }
}
