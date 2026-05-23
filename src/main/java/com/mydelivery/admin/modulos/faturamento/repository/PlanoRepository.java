package com.mydelivery.admin.modulos.faturamento.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mydelivery.admin.modulos.faturamento.entity.Plano;

public interface PlanoRepository extends JpaRepository<Plano, Long> {

    Optional<Plano> findByCodigoIgnoreCase(String codigo);

    List<Plano> findByAtivoOrderByValorMensalAsc(Boolean ativo);

    boolean existsByCodigoIgnoreCase(String codigo);
}
