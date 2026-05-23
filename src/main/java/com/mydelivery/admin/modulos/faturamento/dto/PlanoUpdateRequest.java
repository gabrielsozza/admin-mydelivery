package com.mydelivery.admin.modulos.faturamento.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Atualização parcial. Campos null = não alterar. */
@Data
public class PlanoUpdateRequest {

    @Size(max = 100)
    private String nome;

    @Size(max = 500)
    private String descricao;

    @DecimalMin(value = "0.00")
    private BigDecimal valorMensal;

    private String features;

    private Boolean ativo;
}
