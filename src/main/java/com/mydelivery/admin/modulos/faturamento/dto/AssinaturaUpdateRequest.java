package com.mydelivery.admin.modulos.faturamento.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Atualização parcial. Não permite mudar restaurante ou plano (pra trocar de plano,
 * cancela a atual e cria nova — mantém histórico claro).
 */
@Data
public class AssinaturaUpdateRequest {

    @DecimalMin(value = "0.00")
    private BigDecimal valorMensal;

    @Min(1)
    @Max(28)
    private Integer diaVencimento;
}
