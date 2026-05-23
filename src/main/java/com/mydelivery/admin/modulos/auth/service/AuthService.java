package com.mydelivery.admin.modulos.auth.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mydelivery.admin.modulos.auth.dto.LoginRequest;
import com.mydelivery.admin.modulos.auth.dto.LoginResponse;
import com.mydelivery.admin.modulos.auth.dto.MeResponse;
import com.mydelivery.admin.modulos.auth.entity.AdminUser;
import com.mydelivery.admin.modulos.auth.repository.AdminUserRepository;
import com.mydelivery.admin.security.JwtAdminService;
import com.mydelivery.admin.shared.exception.UnauthorizedException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtAdminService jwtAdminService;

    @Value("${admin.jwt.expiration}")
    private long expirationMs;

    /**
     * Valida email + senha e retorna token JWT.
     * Mesma mensagem genérica pra "usuário não existe" e "senha errada" — não
     * vaza informação pra atacante saber se email existe.
     */
    @Transactional
    public LoginResponse login(LoginRequest req) {
        AdminUser admin = adminUserRepository.findByEmailIgnoreCase(req.getEmail().trim())
                .orElseThrow(() -> new UnauthorizedException("Credenciais inválidas"));

        if (!Boolean.TRUE.equals(admin.getAtivo())) {
            log.warn("[AdminAuth] Tentativa de login em conta DESATIVADA: {}", admin.getEmail());
            throw new UnauthorizedException("Conta desativada");
        }

        if (!passwordEncoder.matches(req.getSenha(), admin.getSenhaHash())) {
            log.warn("[AdminAuth] Senha incorreta pra: {}", admin.getEmail());
            throw new UnauthorizedException("Credenciais inválidas");
        }

        admin.setUltimoLoginEm(LocalDateTime.now());
        adminUserRepository.save(admin);

        String token = jwtAdminService.gerar(admin);
        log.info("[AdminAuth] Login OK — admin={} role={}", admin.getEmail(), admin.getRole());

        return LoginResponse.builder()
                .token(token)
                .adminId(admin.getId())
                .email(admin.getEmail())
                .nome(admin.getNome())
                .role(admin.getRole().name())
                .expiresIn(expirationMs)
                .build();
    }

    public MeResponse me(String email) {
        AdminUser admin = adminUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UnauthorizedException("Sessão inválida"));
        return MeResponse.builder()
                .id(admin.getId())
                .email(admin.getEmail())
                .nome(admin.getNome())
                .role(admin.getRole().name())
                .ativo(admin.getAtivo())
                .ultimoLoginEm(admin.getUltimoLoginEm())
                .build();
    }
}
