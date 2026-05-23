package com.mydelivery.admin.modulos.pagamentos.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

/** Resposta com tudo que o frontend precisa pra renderizar a cobrança PIX. */
@Data
@Builder
public class CobrancaPixDTO {
    private Long faturaId;
    private Long mpPaymentId;
    private BigDecimal valor;
    private String statusMp;
    /** PIX copia-e-cola (string). */
    private String pixCopiaCola;
    /** QR Code já renderizado em base64 (PNG). */
    private String qrCodeBase64;
    /** URL do MP pro cliente abrir e pagar (fallback). */
    private String ticketUrl;
}
