package com.mydelivery.admin.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.mydelivery.admin.modulos.auth.repository.AdminUserRepository;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Filtro JWT do admin. Roda em toda request:
 *  - pega o header Authorization: Bearer <token>
 *  - valida o token via JwtAdminService
 *  - busca o AdminUser no DB (garantir que ainda está ativo)
 *  - popula o SecurityContextHolder com ROLE_<role>
 *
 * Se não tiver token ou for inválido, segue silenciosamente e o SecurityConfig
 * decide se a rota exige auth ou não.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAdminFilter extends OncePerRequestFilter {

    private final JwtAdminService jwtService;
    private final AdminUserRepository adminUserRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {

        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7).trim();
            try {
                String email = jwtService.validarERetornarEmail(token);
                adminUserRepository.findByEmailIgnoreCase(email).ifPresent(admin -> {
                    if (Boolean.TRUE.equals(admin.getAtivo())
                            && SecurityContextHolder.getContext().getAuthentication() == null) {
                        var authority = new SimpleGrantedAuthority("ROLE_" + admin.getRole().name());
                        var authentication = new UsernamePasswordAuthenticationToken(
                                admin.getEmail(), null, List.of(authority));
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                });
            } catch (JwtException e) {
                log.debug("[AdminAuth] token inválido: {}", e.getMessage());
                // segue sem autenticar — Spring nega depois se a rota exigir
            }
        }
        chain.doFilter(req, resp);
    }
}
