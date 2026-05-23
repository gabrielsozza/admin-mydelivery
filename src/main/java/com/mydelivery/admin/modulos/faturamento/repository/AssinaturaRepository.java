package com.mydelivery.admin.modulos.faturamento.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mydelivery.admin.modulos.faturamento.entity.Assinatura;

public interface AssinaturaRepository extends JpaRepository<Assinatura, Long> {

    /** Pega a assinatura ATIVA do restaurante (deve ter no máximo 1). */
    Optional<Assinatura> findFirstByRestauranteIdAndStatus(Long restauranteId, Assinatura.Status status);

    List<Assinatura> findByRestauranteIdOrderByCriadoEmDesc(Long restauranteId);

    /** Todas as ATIVAS — usado pelo scheduler de geração de faturas. */
    List<Assinatura> findByStatus(Assinatura.Status status);

    @Query("""
        SELECT a FROM Assinatura a
        WHERE (:status IS NULL OR a.status = :status)
          AND (:planoId IS NULL OR a.planoId = :planoId)
          AND (:restauranteId IS NULL OR a.restauranteId = :restauranteId)
        """)
    Page<Assinatura> buscar(@Param("status") Assinatura.Status status,
                            @Param("planoId") Long planoId,
                            @Param("restauranteId") Long restauranteId,
                            Pageable pageable);

    /** Soma dos valores mensais das ATIVAS → MRR. */
    @Query("""
        SELECT COALESCE(SUM(a.valorMensal), 0)
          FROM Assinatura a
         WHERE a.status = com.mydelivery.admin.modulos.faturamento.entity.Assinatura.Status.ATIVA
        """)
    BigDecimal somaMrr();

    long countByStatus(Assinatura.Status status);
}
