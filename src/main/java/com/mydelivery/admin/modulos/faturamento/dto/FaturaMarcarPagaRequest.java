package com.mydelivery.admin.modulos.faturamento.dto;

import java.time.LocalDateTime;

import com.mydelivery.admin.modulos.faturamento.entity.Fatura;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FaturaMarcarPagaRequest {

    @NotNull
    private Fatura.MetodoPagamento metodoPagamento;

    /** Se vier null, usa now(). */
    private LocalDateTime pagamentoEm;

    /** Ex.: ID da transação MP, número do comprovante, etc. */
    private String externalPaymentId;

    private String observacao;
}
