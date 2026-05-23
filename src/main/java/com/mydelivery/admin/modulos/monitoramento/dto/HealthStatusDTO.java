package com.mydelivery.admin.modulos.monitoramento.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.mydelivery.admin.modulos.monitoramento.entity.HealthSnapshot;

import lombok.Builder;
import lombok.Data;

/** Estado atual da saúde de um restaurante (último snapshot). */
@Data
@Builder
public class HealthStatusDTO {
    private Long restauranteId;
    private String restauranteNome;
    private LocalDateTime capturadoEm;
    private String statusRestaurante;
    private Boolean aberto;
    private Boolean dentroHorario;
    private Integer score;
    private List<String> problemas;

    public static HealthStatusDTO from(HealthSnapshot s, String nome) {
        return HealthStatusDTO.builder()
                .restauranteId(s.getRestauranteId())
                .restauranteNome(nome)
                .capturadoEm(s.getCapturadoEm())
                .statusRestaurante(s.getStatusRestaurante())
                .aberto(s.getAberto())
                .dentroHorario(s.getDentroHorario())
                .score(s.getScore())
                .problemas(s.getProblemas() == null || s.getProblemas().isBlank()
                        ? List.of()
                        : List.of(s.getProblemas().split("\\s*,\\s*")))
                .build();
    }
}
