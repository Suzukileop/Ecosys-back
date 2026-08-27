package com.plateforme.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plateforme.auth.security.AuthTokenFilter;
import com.plateforme.auth.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final AuthTokenFilter authTokenFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write(new ObjectMapper().writeValueAsString(Map.of(
                                    "timestamp", LocalDateTime.now().toString(),
                                    "status", 401,
                                    "error", "Unauthorized",
                                    "message", "Authentification requise",
                                    "path", request.getRequestURI()
                            )));
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/marketplace/purchases/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/marketplace/products/*/purchase").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/marketplace/creators/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/marketplace/creators/*/view").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/marketplace/creators/*/contact-messages").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/public/feedback").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/marketplace/contents/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/marketplace/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/marketplace/social/reactions/counts").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/marketplace/social/comments").permitAll()
                        /** Fichiers démo/refs servis par URL (img/video n'envoient pas le JWT) */
                        .requestMatchers(HttpMethod.GET, "/api/storage/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/webhook/vpi").permitAll()
                        .requestMatchers(
                                "/api/auth/signup",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/api/auth/oauth/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/api-docs/**",
                                "/v3/api-docs/**",
                                "/ws/**"
                        ).permitAll()
                        .requestMatchers("/api/chat/**").authenticated()
                        .requestMatchers("/api/messaging/**").authenticated()
                        .requestMatchers("/api/presence/**").authenticated()
                        .requestMatchers("/api/ecosystem/**").authenticated()
                        .requestMatchers("/api/agent/**").authenticated()
                        .requestMatchers("/api/notifications/**").authenticated()
                        .requestMatchers("/api/creator/**").authenticated()
                        .requestMatchers("/api/credits/**").authenticated()
                        .requestMatchers("/api/scheduler/**").authenticated()
                        .requestMatchers("/api/analytics/**").authenticated()
                        .requestMatchers("/api/admin/**").authenticated()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl,
            @Value("${app.cors-extra-origins:}") String corsExtraOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(resolveCorsOrigins(frontendUrl, corsExtraOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static List<String> resolveCorsOrigins(String frontendUrl, String corsExtraOrigins) {
        Set<String> origins = new LinkedHashSet<>();
        origins.add("http://localhost:3000");
        if (frontendUrl != null && !frontendUrl.isBlank()) {
            origins.add(stripTrailingSlash(frontendUrl.trim()));
        }
        if (corsExtraOrigins != null && !corsExtraOrigins.isBlank()) {
            Arrays.stream(corsExtraOrigins.split(","))
                    .map(String::trim)
                    .filter(origin -> !origin.isEmpty())
                    .map(SecurityConfig::stripTrailingSlash)
                    .forEach(origins::add);
        }
        return List.copyOf(origins);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
