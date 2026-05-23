package com.mydelivery.admin.shared.main.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mydelivery.admin.shared.main.entity.PagamentoMensalidadeMain;

public interface PagamentoMensalidadeMainRepository extends JpaRepository<PagamentoMensalidadeMain, Long> {

    List<PagamentoMensalidadeMain> findByRestauranteIdOrderByCriadoEmDesc(Long restauranteId);

    @Query("""
        SELECT p FROM PagamentoMensalidadeMain p
        WHERE (:status IS NULL OR p.status = :status)
          AND (:restauranteId IS NULL OR p.restauranteId = :restauranteId)
        ORDER BY p.criadoEm DESC
        """)
    Page<PagamentoMensalidadeMain> buscar(@Param("status") PagamentoMensalidadeMain.Status status,
                                          @Param("restauranteId") Long restauranteId,
                                          Pageable pageable);

    long countByStatus(PagamentoMensalidadeMain.Status status);

    @Query("""
        SELECT COALESCE(SUM(p.valor), 0) FROM PagamentoMensalidadeMain p
        WHERE p.status = com.mydelivery.admin.shared.main.entity.PagamentoMensalidadeMain.Status.PENDENTE
        """)
    BigDecimal somaInadimplencia();

    @Query("""
        SELECT COALESCE(SUM(p.valor), 0) FROM PagamentoMensalidadeMain p
        WHERE p.status = com.mydelivery.admin.shared.main.entity.PagamentoMensalidadeMain.Status.PAGO
          AND p.pagoEm >= :inicio AND p.pagoEm < :fimExcl
        """)
    BigDecimal somaPagosEntre(@Param("inicio") LocalDateTime inicio,
                              @Param("fimExcl") LocalDateTime fimExcl);
}
