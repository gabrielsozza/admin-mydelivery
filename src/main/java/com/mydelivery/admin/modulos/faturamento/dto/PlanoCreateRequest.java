package com.mydelivery.admin.modulos.faturamento.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PlanoCreateRequest {

    @NotBlank
    @Size(max = 50)
    private String codigo;

    @NotBlank
    @Size(max = 100)
    private String nome;

    @Size(max = 500)
    private String descricao;

    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal valorMensal;

    /** JSON livre (string) — frontend serializa. */
    private String features;

    /** Default true. */
    private Boolean ativo;
}
