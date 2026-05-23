package com.mydelivery.admin.modulos.pagamentospedido.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.mydelivery.admin.shared.main.entity.PagamentoPedidoMain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PagamentoPedidoListDTO {
    private Long id;
    private Long pedidoId;
    private String metodo;
    private BigDecimal valor;
    private String status;
    private String mpStatusDetail;
    private String mpStatusDetailAmigavel;
    private LocalDateTime criadoEm;
    private LocalDateTime expiraEm;

    public static PagamentoPedidoListDTO from(PagamentoPedidoMain p) {
        return PagamentoPedidoListDTO.builder()
                .id(p.getId())
                .pedidoId(p.getPedidoId())
                .metodo(p.getMetodo())
                .valor(p.getValor())
                .status(p.getStatus() == null ? null : p.getStatus().name())
                .mpStatusDetail(p.getMpStatusDetail())
                .mpStatusDetailAmigavel(amigavel(p.getMpStatusDetail(), p.getStatus()))
                .criadoEm(p.getCriadoEm())
                .expiraEm(p.getExpiraEm())
                .build();
    }

    /** Traduz status_detail técnico do MP em algo legível pro suporte. */
    public static String amigavel(String detail, PagamentoPedidoMain.Status status) {
        if (detail == null || detail.isBlank()) {
            if (status == PagamentoPedidoMain.Status.EXPIRADO) return "PIX expirou antes do pagamento";
            if (status == PagamentoPedidoMain.Status.PENDENTE) return "Aguardando pagamento";
            return null;
        }
        return switch (detail) {
            case "cc_rejected_call_for_authorize"   -> "Cartão pediu liberação no banco";
            case "cc_rejected_insufficient_amount"  -> "Cartão sem limite";
            case "cc_rejected_bad_filled_card_number" -> "Número do cartão incorreto";
            case "cc_rejected_bad_filled_date"      -> "Data de validade incorreta";
            case "cc_rejected_bad_filled_security_code" -> "CVV incorreto";
            case "cc_rejected_bad_filled_other"     -> "Dados do cartão incorretos";
            case "cc_rejected_high_risk"            -> "Risco alto (anti-fraude)";
            case "cc_rejected_max_attempts"         -> "Muitas tentativas";
            case "cc_rejected_other_reason"         -> "Recusado pelo banco";
            case "rejected_high_risk"               -> "Bloqueado por anti-fraude";
            case "pix_expired", "expired"           -> "PIX expirou";
            default -> detail;
        };
    }
}
