package com.mydelivery.admin.modulos.faturamento.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssinaturaCreateRequest {

    @NotNull
    private Long restauranteId;

    @NotNull
    private Long planoId;

    /** Se vier null, usa o valorMensal do Plano no momento. */
    private BigDecimal valorMensal;

    /** Dia do mês 1-28. Default 10. */
    @Min(1)
    @Max(28)
    private Integer diaVencimento;

    /** Default hoje. */
    private LocalDate inicioEm;
}
