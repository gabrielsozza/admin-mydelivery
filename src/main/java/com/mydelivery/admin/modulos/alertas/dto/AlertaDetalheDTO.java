package com.mydelivery.admin.modulos.alertas.dto;

import java.time.LocalDateTime;

import com.mydelivery.admin.modulos.alertas.entity.Alerta;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlertaDetalheDTO {
    private Long id;
    private String tipo;
    private String severidade;
    private String status;
    private Long restauranteId;
    private String restauranteNome;
    private String titulo;
    private String descricao;
    private String dados;
    private String dedupKey;
    private Integer ocorrencias;
    private LocalDateTime criadoEm;
    private LocalDateTime ultimaOcorrenciaEm;
    private LocalDateTime reconhecidoEm;
    private Long reconhecidoPor;
    private String reconhecidoPorNome;
    private LocalDateTime resolvidoEm;
    private Long resolvidoPor;
    private String resolvidoPorNome;
    private String observacao;

    public static AlertaDetalheDTO from(Alerta a,
                                        String restauranteNome,
                                        String reconhecidoNome,
                                        String resolvidoNome) {
        return AlertaDetalheDTO.builder()
                .id(a.getId())
                .tipo(a.getTipo() == null ? null : a.getTipo().name())
                .severidade(a.getSeveridade() == null ? null : a.getSeveridade().name())
                .status(a.getStatus() == null ? null : a.getStatus().name())
                .restauranteId(a.getRestauranteId())
                .restauranteNome(restauranteNome)
                .titulo(a.getTitulo())
                .descricao(a.getDescricao())
                .dados(a.getDados())
                .dedupKey(a.getDedupKey())
                .ocorrencias(a.getOcorrencias())
                .criadoEm(a.getCriadoEm())
                .ultimaOcorrenciaEm(a.getUltimaOcorrenciaEm())
                .reconhecidoEm(a.getReconhecidoEm())
                .reconhecidoPor(a.getReconhecidoPor())
                .reconhecidoPorNome(reconhecidoNome)
                .resolvidoEm(a.getResolvidoEm())
                .resolvidoPor(a.getResolvidoPor())
                .resolvidoPorNome(resolvidoNome)
                .observacao(a.getObservacao())
                .build();
    }
}
