package com.mydelivery.admin.modulos.restaurantes.dto;

import java.time.LocalDateTime;

import com.mydelivery.admin.shared.main.entity.RestauranteMain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Item de lista pra grid de restaurantes no admin. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RestauranteListDTO {
    private Long id;
    private String nome;
    private String slug;
    private String telefone;
    private String cidade;
    private String estado;
    private String status;            // ATIVO / TRIAL / BLOQUEADO / CANCELADO
    private Boolean aberto;
    private LocalDateTime criadoEm;
    private LocalDateTime trialExpiraEm;

    public static RestauranteListDTO from(RestauranteMain r) {
        return RestauranteListDTO.builder()
                .id(r.getId())
                .nome(r.getNome())
                .slug(r.getSlug())
                .telefone(r.getTelefone())
                .cidade(r.getCidade())
                .estado(r.getEstado())
                .status(r.getStatus() != null ? r.getStatus().name() : null)
                .aberto(r.getAberto())
                .criadoEm(r.getCriadoEm())
                .trialExpiraEm(r.getTrialExpiraEm())
                .build();
    }
}
