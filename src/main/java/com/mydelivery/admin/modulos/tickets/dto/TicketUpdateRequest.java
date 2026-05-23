package com.mydelivery.admin.modulos.tickets.dto;

import com.mydelivery.admin.modulos.tickets.entity.Ticket;

import lombok.Data;

/**
 * Atualização parcial de ticket. Campos null = não mexer.
 * Pra reabrir, mande {@code status=ABERTO}; pra fechar, {@code status=FECHADO}.
 */
@Data
public class TicketUpdateRequest {
    private Ticket.Status status;
    private Ticket.Prioridade prioridade;
    private Ticket.Categoria categoria;
    /** AdminUser.id; {@code -1} = remover atribuição. */
    private Long atribuidoA;
}
