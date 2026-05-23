package com.mydelivery.admin.modulos.tickets.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.mydelivery.admin.modulos.tickets.entity.Ticket;

import lombok.Builder;
import lombok.Data;

/** Ticket completo + thread de mensagens. */
@Data
@Builder
public class TicketDetalheDTO {
    private Long id;
    private Long restauranteId;
    private String restauranteNome;
    private String titulo;
    private String descricao;
    private String status;
    private String prioridade;
    private String categoria;
    private Long criadoPorAdminId;
    private String criadoPorNome;
    private Long atribuidoA;
    private String atribuidoNome;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
    private LocalDateTime fechadoEm;
    private List<MensagemDTO> mensagens;

    public static TicketDetalheDTO from(Ticket t,
                                        String restauranteNome,
                                        String atribuidoNome,
                                        List<MensagemDTO> mensagens) {
        return TicketDetalheDTO.builder()
                .id(t.getId())
                .restauranteId(t.getRestauranteId())
                .restauranteNome(restauranteNome)
                .titulo(t.getTitulo())
                .descricao(t.getDescricao())
                .status(t.getStatus() == null ? null : t.getStatus().name())
                .prioridade(t.getPrioridade() == null ? null : t.getPrioridade().name())
                .categoria(t.getCategoria() == null ? null : t.getCategoria().name())
                .criadoPorAdminId(t.getCriadoPorAdminId())
                .criadoPorNome(t.getCriadoPorNome())
                .atribuidoA(t.getAtribuidoA())
                .atribuidoNome(atribuidoNome)
                .criadoEm(t.getCriadoEm())
                .atualizadoEm(t.getAtualizadoEm())
                .fechadoEm(t.getFechadoEm())
                .mensagens(mensagens)
                .build();
    }
}
