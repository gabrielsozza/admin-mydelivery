package com.mydelivery.admin.modulos.pagamentos.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CobrancaBoletoDTO {
    private Long faturaId;
    private Long mpPaymentId;
    private BigDecimal valor;
    private String statusMp;
    /** URL do PDF/visualização do boleto no MP. */
    private String ticketUrl;
}
