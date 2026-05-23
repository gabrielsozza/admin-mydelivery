package com.mydelivery.admin.modulos.tickets.dto;

import java.util.List;

import com.mydelivery.admin.modulos.tickets.entity.Ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Payload pra criar ticket no painel admin (em nome do restaurante). */
@Data
public class TicketCreateRequest {

    @NotNull
    private Long restauranteId;

    @NotBlank
    @Size(max = 180)
    private String titulo;

    @NotBlank
    private String descricao;

    /** Default MEDIA se vier null. */
    private Ticket.Prioridade prioridade;

    /** Default OUTRO se vier null. */
    private Ticket.Categoria categoria;

    /** URLs de anexo pra primeira mensagem (opcional). */
    private List<String> anexos;
}
