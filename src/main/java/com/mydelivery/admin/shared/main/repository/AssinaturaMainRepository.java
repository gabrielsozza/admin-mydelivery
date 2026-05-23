package com.mydelivery.admin.shared.main.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mydelivery.admin.shared.main.entity.AssinaturaMain;

public interface AssinaturaMainRepository extends JpaRepository<AssinaturaMain, Long> {

    Optional<AssinaturaMain> findFirstByRestauranteIdOrderByIdDesc(Long restauranteId);

    List<AssinaturaMain> findByRestauranteIdOrderByIdDesc(Long restauranteId);

    @Query("""
        SELECT a FROM AssinaturaMain a
        WHERE (:status IS NULL OR a.status = :status)
          AND (:plano IS NULL OR :plano = '' OR a.plano = :plano)
          AND (:restauranteId IS NULL OR a.restauranteId = :restauranteId)
        ORDER BY a.id DESC
        """)
    Page<AssinaturaMain> buscar(@Param("status") AssinaturaMain.Status status,
                                @Param("plano") String plano,
                                @Param("restauranteId") Long restauranteId,
                                Pageable pageable);

    long countByStatus(AssinaturaMain.Status status);

    /** Soma valor das ATIVAS — MRR no main. */
    @Query("""
        SELECT COALESCE(SUM(a.valor), 0) FROM AssinaturaMain a
        WHERE a.status = com.mydelivery.admin.shared.main.entity.AssinaturaMain.Status.ATIVA
        """)
    BigDecimal somaMrr();
}
