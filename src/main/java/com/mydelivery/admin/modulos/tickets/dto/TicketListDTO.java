package com.mydelivery.admin.modulos.tickets.dto;

import java.time.LocalDateTime;

import com.mydelivery.admin.modulos.tickets.entity.Ticket;

import lombok.Builder;
import lombok.Data;

/** Resumo de ticket pra listas/tabelas. */
@Data
@Builder
public class TicketListDTO {
    private Long id;
    private Long restauranteId;
    private String restauranteNome;
    private String titulo;
    private String status;
    private String prioridade;
    private String categoria;
    private Long atribuidoA;
    private String atribuidoNome;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    public static TicketListDTO from(Ticket t, String restauranteNome, String atribuidoNome) {
        return TicketListDTO.builder()
                .id(t.getId())
                .restauranteId(t.getRestauranteId())
                .restauranteNome(restauranteNome)
                .titulo(t.getTitulo())
                .status(t.getStatus() == null ? null : t.getStatus().name())
                .prioridade(t.getPrioridade() == null ? null : t.getPrioridade().name())
                .categoria(t.getCategoria() == null ? null : t.getCategoria().name())
                .atribuidoA(t.getAtribuidoA())
                .atribuidoNome(atribuidoNome)
                .criadoEm(t.getCriadoEm())
                .atualizadoEm(t.getAtualizadoEm())
                .build();
    }
}
