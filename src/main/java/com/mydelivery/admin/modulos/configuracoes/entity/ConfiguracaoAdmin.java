package com.mydelivery.admin.modulos.configuracoes.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tabela key-value pra config do admin (runtime, sem precisar reiniciar).
 *
 * Chaves usadas hoje (Fase 4b já lia via {@code @Value} do .env — agora persistido):
 *   mp.access_token         — Bearer do MP do admin (cobrança SaaS)
 *   mp.webhook_secret       — secret HMAC do webhook MP
 *   mp.public_key           — public key MP (opcional)
 *   mp.ambiente             — "PROD" ou "TEST"
 *   mp.payer_default_email  — email genérico do pagador
 *
 * Flag {@code sensivel} = true mascara o valor nos listings (mostra só prefixo).
 */
@Entity
@Table(name = "configuracao_admin", indexes = {
        @Index(name = "idx_config_chave", columnList = "chave", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfiguracaoAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String chave;

    @Column(columnDefinition = "TEXT")
    private String valor;

    @Column(length = 200)
    private String descricao;

    @Column(nullable = false)
    private Boolean sensivel;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (criadoEm == null) criadoEm = now;
        atualizadoEm = now;
        if (sensivel == null) sensivel = false;
    }

    @PreUpdate
    void preUpdate() {
        atualizadoEm = LocalDateTime.now();
    }
}
