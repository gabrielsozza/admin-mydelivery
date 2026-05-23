package com.mydelivery.admin.modulos.insights.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

/** Painel "à primeira vista". Junta KPIs financeiros + plataforma. */
@Data
@Builder
public class VisaoGeralDTO {
    // ─── Plataforma ───
    private long restaurantesAtivos;
    private long restaurantesTrial;
    private long restaurantesBloqueados;
    private long restaurantesCancelados;

    private long restaurantesNovosUltimos30Dias;

    // ─── Financeiro ───
    private BigDecimal mrr;
    private BigDecimal arr;
    private BigDecimal recebidoMesAtual;
    private BigDecimal recebidoMesAnterior;
    private BigDecimal inadimplenciaTotal;

    // ─── Volume (GMV — do main) ───
    /** Soma de valor_total dos pedidos não-cancelados nos últimos 30 dias. */
    private BigDecimal gmvUltimos30Dias;
    /** Mesma janela, mês anterior (30-60 dias atrás). */
    private BigDecimal gmvMesAnterior;
    private long pedidosUltimos30Dias;

    // ─── Suporte / Saúde ───
    private long ticketsAbertos;
    private long alertasAtivos;
    private long alertasAtivosAltos;
    private long recoveryEmAndamento;
}
