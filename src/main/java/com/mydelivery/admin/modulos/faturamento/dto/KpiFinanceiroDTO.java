package com.mydelivery.admin.modulos.faturamento.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

/** Métricas financeiras agregadas pro dashboard. */
@Data
@Builder
public class KpiFinanceiroDTO {
    /** MRR — Monthly Recurring Revenue (soma valor mensal das assinaturas ATIVAS). */
    private BigDecimal mrr;

    /** ARR — Annual Recurring Revenue (MRR × 12). */
    private BigDecimal arr;

    /** Total já vencido e não pago. */
    private BigDecimal inadimplenciaTotal;

    /** Pagamentos recebidos no mês corrente. */
    private BigDecimal recebidoMesAtual;

    /** Pagamentos recebidos no mês passado (pra comparativo). */
    private BigDecimal recebidoMesAnterior;

    private Long assinaturasAtivas;
    private Long assinaturasSuspensas;
    private Long assinaturasCanceladas;

    private Long faturasPendentes;
    private Long faturasVencidas;
    private Long faturasPagas;
}
