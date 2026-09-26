package com.plateforme.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthTokenFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = parseJwt(request);
            if (jwt != null && jwtUtils.validateToken(jwt)) {
                String jti = jwtUtils.extractJti(jwt);

                RevocationCheck revocation = checkRevocation(jti);
                if (revocation != RevocationCheck.ALLOWED) {
                    if (revocation == RevocationCheck.REVOKED) {
                        log.warn("Token révoqué détecté, jti: {}", jti);
                    }
                    filterChain.doFilter(request, response);
                    return;
                }

                String username = jwtUtils.extractUsername(jwt);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            log.error("Impossible de définir l'authentification utilisateur: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }

    /**
     * Resultat de la verification de revocation. `UNAVAILABLE` est volontairement distinct de
     * `REVOKED` : les deux refusent la requete, mais un Redis injoignable n'est pas un token
     * revoque, et confondre les deux dans les logs transforme une panne d'infrastructure en
     * "probleme d'authentification" — c'est exactement ce qui a fait durer la panne du 25/09.
     */
    private enum RevocationCheck { ALLOWED, REVOKED, UNAVAILABLE }

    /**
     * Redis porte la liste de revocation. S'il est injoignable, on ne peut pas prouver que le
     * token est toujours valide : on echoue *ferme* (requete poursuivie sans authentification)
     * plutot que d'accorder l'acces sur la foi d'une verification qu'on n'a pas pu faire.
     * Combine au `timeout` de 500ms cote configuration, l'echec est desormais immediat au lieu
     * d'attendre 60s. Passer en "fail open" (accepter le token quand Redis est absent) est
     * possible mais c'est un arbitrage de securite : pendant la panne, les tokens revoques
     * redeviendraient valides.
     */
    private RevocationCheck checkRevocation(String jti) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + jti))
                    ? RevocationCheck.REVOKED
                    : RevocationCheck.ALLOWED;
        } catch (RuntimeException e) {
            log.error("Liste de révocation Redis indisponible — requête traitée comme "
                    + "non authentifiée (ce n'est pas un problème d'identifiants): {}", e.getMessage());
            return RevocationCheck.UNAVAILABLE;
        }
    }
}
