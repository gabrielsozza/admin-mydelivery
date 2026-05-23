package com.mydelivery.admin.modulos.faturamento.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AssinaturaCancelRequest {

    @Size(max = 300)
    private String motivo;

    /** Se true, cancela também faturas PENDENTES dessa assinatura. */
    private Boolean cancelarFaturasPendentes;
}
