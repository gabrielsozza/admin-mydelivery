package com.mydelivery.admin.modulos.pagamentospedido.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PagamentoPedidoResumoDTO {
    private long aprovados;
    private long recusados;
    private long expirados;
    private long cancelados;
    private long pendentes;
    /** Topo de motivos de falha nos últimos 30 dias. */
    private List<MotivoFalha> topMotivos;

    @Data
    @Builder
    public static class MotivoFalha {
        private String detail;
        private String amigavel;
        private long ocorrencias;
    }
}
