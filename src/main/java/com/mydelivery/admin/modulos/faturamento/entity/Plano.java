package com.mydelivery.admin.modulos.faturamento.entity;

import java.math.BigDecimal;
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
 * Catálogo de planos de assinatura do SaaS MyDelivery.
 *
 * Cada Assinatura aponta pra um Plano, mas guarda um SNAPSHOT do valor no momento
 * da contratação ({@code Assinatura.valorMensal}). Assim, se você reajustar o
 * preço do plano amanhã, restaurantes já assinados continuam pagando o valor
 * antigo até você renegociar manualmente.
 */
@Entity
@Table(name = "plano", indexes = {
        @Index(name = "idx_plano_ativo", columnList = "ativo"),
        @Index(name = "idx_plano_codigo", columnList = "codigo", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plano {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificador slug-like estável pra integrações (ex.: "starter", "pro"). */
    @Column(nullable = false, length = 50, unique = true)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(length = 500)
    private String descricao;

    @Column(name = "valor_mensal", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorMensal;

    /** JSON livre com features (lista de strings, limites, etc). */
    @Column(name = "features", columnDefinition = "TEXT")
    private String features;

    @Column(nullable = false)
    private Boolean ativo;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (criadoEm == null) criadoEm = now;
        atualizadoEm = now;
        if (ativo == null) ativo = true;
    }

    @PreUpdate
    void preUpdate() {
        atualizadoEm = LocalDateTime.now();
    }
}
