package com.mydelivery.admin.modulos.alertas.dto;

import java.time.LocalDateTime;

import com.mydelivery.admin.modulos.alertas.entity.Alerta;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlertaListDTO {
    private Long id;
    private String tipo;
    private String severidade;
    private String status;
    private Long restauranteId;
    private String restauranteNome;
    private String titulo;
    private Integer ocorrencias;
    private LocalDateTime criadoEm;
    private LocalDateTime ultimaOcorrenciaEm;

    public static AlertaListDTO from(Alerta a, String restauranteNome) {
        return AlertaListDTO.builder()
                .id(a.getId())
                .tipo(a.getTipo() == null ? null : a.getTipo().name())
                .severidade(a.getSeveridade() == null ? null : a.getSeveridade().name())
                .status(a.getStatus() == null ? null : a.getStatus().name())
                .restauranteId(a.getRestauranteId())
                .restauranteNome(restauranteNome)
                .titulo(a.getTitulo())
                .ocorrencias(a.getOcorrencias())
                .criadoEm(a.getCriadoEm())
                .ultimaOcorrenciaEm(a.getUltimaOcorrenciaEm())
                .build();
    }
}
