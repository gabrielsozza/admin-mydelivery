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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Liga um restaurante a um plano com valor + dia de vencimento.
 *
 * Um restaurante pode ter várias Assinaturas ao longo do tempo, mas só UMA ATIVA.
 * Cancelar = setar status=CANCELADA + fimEm; pra trocar de plano, cancela e cria
 * outra (mantém histórico).
 *
 * {@code valorMensal} é snapshot — mesmo que o catálogo Plano mude, esta assinatura
 * mantém o preço acertado até alguém reajustar manualmente.
 */
@Entity
@Table(name = "assinatura", indexes = {
        @Index(name = "idx_assin_restaurante", columnList = "restaurante_id"),
        @Index(name = "idx_assin_status", columnList = "status"),
        @Index(name = "idx_assin_plano", columnList = "plano_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assinatura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurante_id", nullable = false)
    private Long restauranteId;

    @Column(name = "plano_id", nullable = false)
    private Long planoId;

    /** Snapshot do nome do plano no momento da contratação (auditoria). */
    @Column(name = "plano_nome", nullable = false, length = 100)
    private String planoNome;

    @Column(name = "valor_mensal", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorMensal;

    /** Dia do mês pra vencer (1-28). Limitado a 28 pra evitar mês curto. */
    @Column(name = "dia_vencimento", nullable = false)
    private Integer diaVencimento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "inicio_em", nullable = false)
    private LocalDate inicioEm;

    @Column(name = "fim_em")
    private LocalDate fimEm;

    @Column(name = "motivo_cancelamento", length = 300)
    private String motivoCancelamento;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (criadoEm == null) criadoEm = now;
        atualizadoEm = now;
        if (status == null) status = Status.ATIVA;
        if (inicioEm == null) inicioEm = LocalDate.now();
        if (diaVencimento == null) diaVencimento = 10;
        if (diaVencimento < 1 || diaVencimento > 28) diaVencimento = 10;
    }

    @PreUpdate
    void preUpdate() {
        atualizadoEm = LocalDateTime.now();
        if (status == Status.CANCELADA && fimEm == null) fimEm = LocalDate.now();
    }

    public enum Status {
        ATIVA, SUSPENSA, CANCELADA
    }
}
