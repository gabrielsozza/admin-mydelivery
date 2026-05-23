package com.mydelivery.admin.security;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.mydelivery.admin.modulos.auth.entity.AdminUser;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * Gera e valida JWT do admin.
 *
 * Token contém:
 *  - subject: email do admin
 *  - claim "uid": id do admin
 *  - claim "role": ADMIN / SUPORTE / FINANCEIRO
 *  - claim "type": "admin" (pra diferenciar de tokens do app dos restaurantes)
 *
 * O secret vem do env var ADMIN_JWT_SECRET (Base64). NUNCA reusar o secret
 * do projeto principal — admin tem chave própria pra que vazamento de um lado
 * não comprometa o outro.
 */
@Service
public class JwtAdminService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtAdminService(
            @Value("${admin.jwt.secret}") String secretBase64,
            @Value("${admin.jwt.expiration}") long expirationMs) {
        byte[] keyBytes = Decoders.BASE64.decode(secretBase64);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    /** Gera token pra um admin autenticado. */
    public String gerar(AdminUser admin) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(admin.getEmail())
                .claim("uid", admin.getId())
                .claim("role", admin.getRole().name())
                .claim("type", "admin")
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    /** Valida e retorna o email (subject) se OK. Lança JwtException se inválido/expirado. */
    public String validarERetornarEmail(String token) throws JwtException {
        Claims claims = parse(token).getPayload();
        // Garante que é um token do admin (não vazou um JWT do app dos restaurantes)
        if (!"admin".equals(claims.get("type"))) {
            throw new JwtException("token não é de admin");
        }
        return claims.getSubject();
    }

    public Claims claims(String token) {
        return parse(token).getPayload();
    }

    private io.jsonwebtoken.Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
    }
}
