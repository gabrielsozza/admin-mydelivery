package com.mydelivery.admin.modulos.insights.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;
import lombok.Data;

/** Indicador GMV + série mensal. */
@Data
@Builder
public class GmvDTO {
    private BigDecimal totalPeriodo;
    private long pedidosPeriodo;
    /** Ticket médio = totalPeriodo / pedidosPeriodo (0 se sem pedidos). */
    private BigDecimal ticketMedio;
    private List<SerieMensalDTO> serieMensal;
}
