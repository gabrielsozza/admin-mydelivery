package com.mydelivery.admin.modulos.faturamento.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.mydelivery.admin.modulos.faturamento.entity.Plano;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlanoDTO {
    private Long id;
    private String codigo;
    private String nome;
    private String descricao;
    private BigDecimal valorMensal;
    private String features;
    private Boolean ativo;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    public static PlanoDTO from(Plano p) {
        return PlanoDTO.builder()
                .id(p.getId())
                .codigo(p.getCodigo())
                .nome(p.getNome())
                .descricao(p.getDescricao())
                .valorMensal(p.getValorMensal())
                .features(p.getFeatures())
                .ativo(p.getAtivo())
                .criadoEm(p.getCriadoEm())
                .atualizadoEm(p.getAtualizadoEm())
                .build();
    }
}
