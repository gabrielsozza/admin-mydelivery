package com.mydelivery.admin.modulos.faturamento.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fatura mensal de uma Assinatura.
 *
 * Gerada pelo scheduler dia 1 de cada mês 06h ({@code FaturamentoScheduler}).
 * UNIQUE em (assinaturaId, competencia) evita duplicata se o scheduler rodar 2x.
 *
 * Fluxo:
 *  PENDENTE → (data > vencimentoEm) → VENCIDA
 *  PENDENTE/VENCIDA → PAGA (manual ou via webhook MP — Fase 4b)
 *  PENDENTE/VENCIDA → CANCELADA (admin)
 *
 * {@code competencia} = "yyyy-MM" da competência (mês a que a fatura se refere).
 * Diferente de {@code vencimentoEm}, que pode ser dia 10 do mês seguinte por ex.
 */
@Entity
@Table(name = "fatura",
        indexes = {
                @Index(name = "idx_fat_assinatura", columnList = "assinatura_id"),
                @Index(name = "idx_fat_restaurante", columnList = "restaurante_id"),
                @Index(name = "idx_fat_status", columnList = "status"),
                @Index(name = "idx_fat_vencimento", columnList = "vencimento_em"),
                @Index(name = "idx_fat_competencia", columnList = "competencia")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_fat_assin_competencia",
                        columnNames = {"assinatura_id", "competencia"})
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fatura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assinatura_id", nullable = false)
    private Long assinaturaId;

    @Column(name = "restaurante_id", nullable = false)
    private Long restauranteId;

    /** Snapshot do nome do plano pra renderizar em PDF/listagem sem JOIN. */
    @Column(name = "plano_nome", nullable = false, length = 100)
    private String planoNome;

    /** "yyyy-MM" — mês a que a fatura se refere. */
    @Column(name = "competencia", nullable = false, length = 7)
    private String competencia;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "vencimento_em", nullable = false)
    private LocalDate vencimentoEm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "pagamento_em")
    private LocalDateTime pagamentoEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pagamento", length = 30)
    private MetodoPagamento metodoPagamento;

    /** ID externo do MP (ou outro gateway) — preenche na Fase 4b ou manualmente. */
    @Column(name = "external_payment_id", length = 100)
    private String externalPaymentId;

    /** Anotação do admin (motivo cancelamento, observação de pagamento, etc). */
    @Column(columnDefinition = "TEXT")
    private String observacao;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (criadoEm == null) criadoEm = now;
        atualizadoEm = now;
        if (status == null) status = Status.PENDENTE;
    }

    @PreUpdate
    void preUpdate() {
        atualizadoEm = LocalDateTime.now();
    }

    public enum Status {
        PENDENTE, PAGA, VENCIDA, CANCELADA
    }

    public enum MetodoPagamento {
        PIX, BOLETO, CARTAO, TRANSFERENCIA, OUTRO
    }
}
