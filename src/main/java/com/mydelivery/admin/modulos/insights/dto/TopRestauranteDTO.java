package com.mydelivery.admin.modulos.insights.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

/**
 * Linha de um ranking de restaurantes.
 *
 *  - Quando o ranking é por GMV: {@code valor} = soma valor_total, {@code quantidade} = nº pedidos
 *  - Quando o ranking é por MRR: {@code valor} = valorMensal da assinatura, {@code quantidade} = 0 (não usa)
 */
@Data
@Builder
public class TopRestauranteDTO {
    private Long restauranteId;
    private String restauranteNome;
    private BigDecimal valor;
    private long quantidade;
}
