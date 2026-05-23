package com.mydelivery.admin.modulos.insights.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

/** Ponto de uma série temporal mensal (pra gráficos de barra/linha). */
@Data
@Builder
public class SerieMensalDTO {
    /** "yyyy-MM" — pronto pra renderizar no eixo X. */
    private String competencia;
    /** Soma (GMV, faturamento, etc). */
    private BigDecimal valor;
    /** Contagem (pedidos, faturas, restaurantes). */
    private long quantidade;
}
