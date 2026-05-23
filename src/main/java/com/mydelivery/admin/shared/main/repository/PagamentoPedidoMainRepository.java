package com.mydelivery.admin.shared.main.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mydelivery.admin.shared.main.entity.PagamentoPedidoMain;

public interface PagamentoPedidoMainRepository extends JpaRepository<PagamentoPedidoMain, Long> {

    @Query("""
        SELECT p FROM PagamentoPedidoMain p
        WHERE p.status <> com.mydelivery.admin.shared.main.entity.PagamentoPedidoMain.Status.APROVADO
          AND (:status IS NULL OR p.status = :status)
          AND p.criadoEm >= :desde
        ORDER BY p.criadoEm DESC
        """)
    Page<PagamentoPedidoMain> findFalhasDesde(@Param("status") PagamentoPedidoMain.Status status,
                                              @Param("desde") LocalDateTime desde,
                                              Pageable pageable);

    long countByStatus(PagamentoPedidoMain.Status status);

    /** Falhas em janela X (últimos N dias) por status_detail (top motivos). */
    @Query("""
        SELECT p.mpStatusDetail, COUNT(p) FROM PagamentoPedidoMain p
        WHERE p.status IN (com.mydelivery.admin.shared.main.entity.PagamentoPedidoMain.Status.RECUSADO,
                            com.mydelivery.admin.shared.main.entity.PagamentoPedidoMain.Status.EXPIRADO,
                            com.mydelivery.admin.shared.main.entity.PagamentoPedidoMain.Status.CANCELADO)
          AND p.criadoEm >= :desde
          AND p.mpStatusDetail IS NOT NULL
        GROUP BY p.mpStatusDetail
        ORDER BY COUNT(p) DESC
        """)
    List<Object[]> topMotivosFalha(@Param("desde") LocalDateTime desde, Pageable pageable);
}
