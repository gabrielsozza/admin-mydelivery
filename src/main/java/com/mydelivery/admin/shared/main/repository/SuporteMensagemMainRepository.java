package com.mydelivery.admin.shared.main.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mydelivery.admin.shared.main.entity.SuporteMensagemMain;

public interface SuporteMensagemMainRepository extends JpaRepository<SuporteMensagemMain, Long> {
    List<SuporteMensagemMain> findByTicketIdOrderByCriadoEmAsc(Long ticketId);
}
