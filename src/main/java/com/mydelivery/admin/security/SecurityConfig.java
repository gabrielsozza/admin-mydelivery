package com.mydelivery.admin.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAdminFilter jwtAdminFilter;

    @Value("${admin.cors.allowed-origins:}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/admin/health").permitAll()
                .requestMatchers("/api/admin/auth/**").permitAll()
                .requestMatchers("/api/admin/mp-webhook").permitAll()
                .anyRequest().hasAnyRole("ADMIN", "SUPORTE", "FINANCEIRO")
                )
                .addFilterBefore(jwtAdminFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    /**
     * CORS bulletproof — combina lista da env + fallback hardcoded com os
     * domínios oficiais do admin (Netlify e mydeliveryfood.com.br). Mesmo que
     * a env esteja vazia ou mal copiada, esses domínios sempre passam.
     *
     * Usa {@code setAllowedOriginPatterns} (aceita wildcard e funciona com
     * credentials=true). Patterns aceitos:
     *   - https://admin.mydeliveryfood.com.br
     *   - https://admin-mydelivery.netlify.app
     *   - https://*.netlify.app  (deploys-preview do Netlify)
     *   - localhost (Live Server)
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> envOrigins = (allowedOrigins == null || allowedOrigins.isBlank())
                ? List.of()
                : Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim).filter(s -> !s.isBlank()).toList();

        // Fallback hardcoded — sobrevive se env var faltar ou estiver corrompida.
        List<String> fallback = List.of(
                "https://admin.mydeliveryfood.com.br",
                "https://admin-mydelivery.netlify.app",
                "https://*.netlify.app",
                "http://localhost:5500",
                "http://127.0.0.1:5500",
                "http://localhost:5501",
                "http://127.0.0.1:5501"
        );

        // Merge: prioriza env (caso queira sobrescrever) + sempre inclui fallback.
        java.util.Set<String> merged = new java.util.LinkedHashSet<>();
        merged.addAll(envOrigins);
        merged.addAll(fallback);
        List<String> finalOrigins = List.copyOf(merged);

        log.info("[CORS] origins permitidos: {}", finalOrigins);

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(finalOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
