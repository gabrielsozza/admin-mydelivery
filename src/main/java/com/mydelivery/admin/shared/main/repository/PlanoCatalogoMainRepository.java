package com.mydelivery.admin.shared.main.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mydelivery.admin.shared.main.entity.PlanoCatalogoMain;

public interface PlanoCatalogoMainRepository extends JpaRepository<PlanoCatalogoMain, Long> {
    List<PlanoCatalogoMain> findAllByOrderByOrdemAscIdAsc();
    Optional<PlanoCatalogoMain> findByCodigoIgnoreCase(String codigo);
}
