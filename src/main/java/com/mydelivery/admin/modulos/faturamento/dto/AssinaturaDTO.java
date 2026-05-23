package com.mydelivery.admin.modulos.faturamento.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.mydelivery.admin.modulos.faturamento.entity.Assinatura;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssinaturaDTO {
    private Long id;
    private Long restauranteId;
    private String restauranteNome;
    private Long planoId;
    private String planoNome;
    private BigDecimal valorMensal;
    private Integer diaVencimento;
    private String status;
    private LocalDate inicioEm;
    private LocalDate fimEm;
    private String motivoCancelamento;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    public static AssinaturaDTO from(Assinatura a, String restauranteNome) {
        return AssinaturaDTO.builder()
                .id(a.getId())
                .restauranteId(a.getRestauranteId())
                .restauranteNome(restauranteNome)
                .planoId(a.getPlanoId())
                .planoNome(a.getPlanoNome())
                .valorMensal(a.getValorMensal())
                .diaVencimento(a.getDiaVencimento())
                .status(a.getStatus() == null ? null : a.getStatus().name())
                .inicioEm(a.getInicioEm())
                .fimEm(a.getFimEm())
                .motivoCancelamento(a.getMotivoCancelamento())
                .criadoEm(a.getCriadoEm())
                .atualizadoEm(a.getAtualizadoEm())
                .build();
    }
}
