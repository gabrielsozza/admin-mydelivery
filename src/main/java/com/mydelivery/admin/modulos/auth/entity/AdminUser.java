package com.mydelivery.admin.modulos.auth.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Usuário do painel administrativo. SEPARADO dos usuários dos restaurantes.
 *
 * Login independente. Inicialmente apenas você. No futuro pode ter colegas
 * com role SUPORTE (acesso limitado a tickets) ou FINANCEIRO (só faturamento).
 */
@Entity
@Table(name = "admin_user")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 200)
    private String senhaHash;

    @Column(length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Column(nullable = false)
    private Boolean ativo;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "ultimo_login_em")
    private LocalDateTime ultimoLoginEm;

    @PrePersist
    void prePersist() {
        if (criadoEm == null) criadoEm = LocalDateTime.now();
        if (ativo == null) ativo = true;
        if (role == null) role = Role.ADMIN;
    }

    public enum Role {
        /** Acesso total ao painel admin. */
        ADMIN,
        /** Acesso limitado a tickets (futuro). */
        SUPORTE,
        /** Acesso só a faturamento (futuro). */
        FINANCEIRO
    }
}
