package com.mydelivery.admin.modulos.insights.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Conversão TRIAL → ATIVO.
 *
 * Sem histórico de transições (snapshots), usamos como aproximação:
 *   {@code totalRestaurantes} = ATIVO + TRIAL + BLOQUEADO + CANCELADO
 *   {@code converteramOuForam} = ATIVO (já passou pelo trial) + BLOQUEADO/CANCELADO (passaram em algum momento)
 *   {@code emTrialAgora}        = TRIAL
 *
 * Taxa = (totalRestaurantes - emTrialAgora) / totalRestaurantes
 *
 * Pra precisão real, precisaríamos coluna {@code virou_ativo_em} no main ou
 * snapshots históricos — fica como melhoria futura.
 */
@Data
@Builder
public class ConversaoTrialDTO {
    private long totalRestaurantes;
    private long emTrialAgora;
    private long ativos;
    private long bloqueados;
    private long cancelados;
    /** Aproximação simples (1 - emTrialAgora/total). */
    private double taxaConversaoAproximada;
}
