package com.mydelivery.admin.modulos.tickets.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mydelivery.admin.modulos.tickets.entity.TicketMensagem;

public interface TicketMensagemRepository extends JpaRepository<TicketMensagem, Long> {

    List<TicketMensagem> findByTicketIdOrderByCriadoEmAsc(Long ticketId);

    long countByTicketIdAndLidaPeloAdminFalse(Long ticketId);
}
