package com.mydelivery.admin.modulos.faturamento.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.mydelivery.admin.modulos.faturamento.entity.Fatura;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FaturaDTO {
    private Long id;
    private Long assinaturaId;
    private Long restauranteId;
    private String restauranteNome;
    private String planoNome;
    private String competencia;
    private BigDecimal valor;
    private LocalDate vencimentoEm;
    private String status;
    private LocalDateTime pagamentoEm;
    private String metodoPagamento;
    private String externalPaymentId;
    private String observacao;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    public static FaturaDTO from(Fatura f, String restauranteNome) {
        return FaturaDTO.builder()
                .id(f.getId())
                .assinaturaId(f.getAssinaturaId())
                .restauranteId(f.getRestauranteId())
                .restauranteNome(restauranteNome)
                .planoNome(f.getPlanoNome())
                .competencia(f.getCompetencia())
                .valor(f.getValor())
                .vencimentoEm(f.getVencimentoEm())
                .status(f.getStatus() == null ? null : f.getStatus().name())
                .pagamentoEm(f.getPagamentoEm())
                .metodoPagamento(f.getMetodoPagamento() == null ? null : f.getMetodoPagamento().name())
                .externalPaymentId(f.getExternalPaymentId())
                .observacao(f.getObservacao())
                .criadoEm(f.getCriadoEm())
                .atualizadoEm(f.getAtualizadoEm())
                .build();
    }
}
