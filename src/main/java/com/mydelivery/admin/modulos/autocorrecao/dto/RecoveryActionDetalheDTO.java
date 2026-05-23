package com.mydelivery.admin.modulos.autocorrecao.dto;

import java.time.LocalDateTime;

import com.mydelivery.admin.modulos.autocorrecao.entity.RecoveryAction;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecoveryActionDetalheDTO {
    private Long id;
    private String tipo;
    private String status;
    private Long restauranteId;
    private String restauranteNome;
    private String dedupKey;
    private String payload;
    private String resultado;
    private String solicitadoPor;
    private Integer tentativas;
    private Integer maxTentativas;
    private LocalDateTime criadoEm;
    private LocalDateTime ultimaTentativaEm;
    private LocalDateTime proximaTentativaEm;
    private LocalDateTime finalizadoEm;
    private Long alertaEmitidoId;

    public static RecoveryActionDetalheDTO from(RecoveryAction r, String restauranteNome) {
        return RecoveryActionDetalheDTO.builder()
                .id(r.getId())
                .tipo(r.getTipo() == null ? null : r.getTipo().name())
                .status(r.getStatus() == null ? null : r.getStatus().name())
                .restauranteId(r.getRestauranteId())
                .restauranteNome(restauranteNome)
                .dedupKey(r.getDedupKey())
                .payload(r.getPayload())
                .resultado(r.getResultado())
                .solicitadoPor(r.getSolicitadoPor())
                .tentativas(r.getTentativas())
                .maxTentativas(r.getMaxTentativas())
                .criadoEm(r.getCriadoEm())
                .ultimaTentativaEm(r.getUltimaTentativaEm())
                .proximaTentativaEm(r.getProximaTentativaEm())
                .finalizadoEm(r.getFinalizadoEm())
                .alertaEmitidoId(r.getAlertaEmitidoId())
                .build();
    }
}
