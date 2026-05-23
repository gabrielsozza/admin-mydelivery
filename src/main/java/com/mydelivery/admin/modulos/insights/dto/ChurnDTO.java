package com.mydelivery.admin.modulos.insights.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * Métricas de churn ao longo dos meses.
 *
 * Aqui usamos como proxy de churn o número de restaurantes BLOQUEADOS no período
 * (bloqueado_em entre inicio-fim). O CANCELADO também é considerado churn forte
 * (cliente saiu definitivamente).
 *
 * {@code taxa} = bloqueados / (ativos no início do mês). Sem snapshots históricos
 * de ativos, usamos uma aproximação: ativos atuais. Tosca mas suficiente pra ter
 * direção.
 */
@Data
@Builder
public class ChurnDTO {
    /** Janela analisada (yyyy-MM até yyyy-MM). */
    private String periodoInicio;
    private String periodoFim;

    /** Total de restaurantes bloqueados/cancelados no período. */
    private long churnAbsoluto;

    /** Aproximação da taxa (proxy — sem histórico real de "ativos no início"). */
    private double taxaPercentualAproximada;

    /** Distribuição mensal. */
    private List<SerieMensalDTO> serieMensal;
}
