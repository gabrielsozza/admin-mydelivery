package com.mydelivery.admin.shared.main.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mydelivery.admin.shared.main.entity.SuporteAnexoMain;

public interface SuporteAnexoMainRepository extends JpaRepository<SuporteAnexoMain, Long> {
    List<SuporteAnexoMain> findByMensagemIdIn(List<Long> mensagemIds);
}
