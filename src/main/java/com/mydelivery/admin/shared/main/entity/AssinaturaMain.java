package com.mydelivery.admin.shared.main.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Espelho READ-ONLY de {@code assinaturas} no banco principal.
 *
 * Mapeia {@code restaurante_id} direto como Long (em vez de @OneToOne) pra
 * evitar coupling com a entidade Restaurante. O nome do restaurante é resolvido
 * via {@link RestauranteMainRepository} no service.
 *
 * Status do main: TRIAL | ATIVA | INADIMPLENTE | CANCELADA
 * Plano (FK enum): MENSAL | SEMESTRAL | ANUAL (null durante TRIAL)
 */
@Entity
@Table(name = "assinaturas")
@Data
@NoArgsConstructor
public class AssinaturaMain {

    @Id
    private Long id;

    @Column(name = "restaurante_id")
    private Long restauranteId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Status status;

    private BigDecimal valor;

    /** Enum: MENSAL/SEMESTRAL/ANUAL ou null em TRIAL. Mantemos como String pra tolerância. */
    @Column(length = 20)
    private String plano;

    @Column(name = "valida_ate")
    private LocalDateTime validaAte;

    @Column(name = "trial_inicio")
    private LocalDateTime trialInicio;

    @Column(name = "trial_fim")
    private LocalDateTime trialFim;

    @Column(name = "proxima_cobranca")
    private LocalDateTime proximaCobranca;

    @Column(name = "ultima_cobranca")
    private LocalDateTime ultimaCobranca;

    @Column(name = "cancelado_em")
    private LocalDateTime canceladoEm;

    public enum Status {
        TRIAL, ATIVA, INADIMPLENTE, CANCELADA
    }
}
