package com.mydelivery.admin.modulos.restaurantes.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.mydelivery.admin.shared.main.entity.RestauranteMain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Detalhe completo de 1 restaurante pra página de drilldown. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RestauranteDetalheDTO {
    private Long id;
    private String nome;
    private String slug;
    private String cnpj;
    private String logoUrl;
    private String telefone;
    private String endereco;
    private String cidade;
    private String estado;
    private Boolean aberto;
    private Integer tempoEntrega;
    private BigDecimal taxaEntrega;
    private BigDecimal pedidoMinimo;
    private String status;
    private LocalDateTime trialExpiraEm;
    private LocalDateTime bloqueadoEm;
    private String motivoBloqueio;
    private LocalDateTime criadoEm;

    public static RestauranteDetalheDTO from(RestauranteMain r) {
        return RestauranteDetalheDTO.builder()
                .id(r.getId())
                .nome(r.getNome())
                .slug(r.getSlug())
                .cnpj(r.getCnpj())
                .logoUrl(r.getLogoUrl())
                .telefone(r.getTelefone())
                .endereco(r.getEndereco())
                .cidade(r.getCidade())
                .estado(r.getEstado())
                .aberto(r.getAberto())
                .tempoEntrega(r.getTempoEntrega())
                .taxaEntrega(r.getTaxaEntrega())
                .pedidoMinimo(r.getPedidoMinimo())
                .status(r.getStatus() != null ? r.getStatus().name() : null)
                .trialExpiraEm(r.getTrialExpiraEm())
                .bloqueadoEm(r.getBloqueadoEm())
                .motivoBloqueio(r.getMotivoBloqueio())
                .criadoEm(r.getCriadoEm())
                .build();
    }
}
