package com.mydelivery.admin.modulos.faturamento.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mydelivery.admin.modulos.faturamento.entity.Fatura;

public interface FaturaRepository extends JpaRepository<Fatura, Long> {

    Optional<Fatura> findByAssinaturaIdAndCompetencia(Long assinaturaId, String competencia);

    boolean existsByAssinaturaIdAndCompetencia(Long assinaturaId, String competencia);

    List<Fatura> findByRestauranteIdOrderByVencimentoEmDesc(Long restauranteId);

    @Query("""
        SELECT f FROM Fatura f
        WHERE (:status IS NULL OR f.status = :status)
          AND (:restauranteId IS NULL OR f.restauranteId = :restauranteId)
          AND (:competencia IS NULL OR :competencia = '' OR f.competencia = :competencia)
        """)
    Page<Fatura> buscar(@Param("status") Fatura.Status status,
                        @Param("restauranteId") Long restauranteId,
                        @Param("competencia") String competencia,
                        Pageable pageable);

    /** PENDENTES com vencimento já passado → o scheduler usa pra marcar VENCIDA. */
    @Query("""
        SELECT f FROM Fatura f
        WHERE f.status = com.mydelivery.admin.modulos.faturamento.entity.Fatura.Status.PENDENTE
          AND f.vencimentoEm < :hoje
        """)
    List<Fatura> findPendentesVencidas(@Param("hoje") LocalDate hoje);

    /** Soma das VENCIDAS → indicador de inadimplência total. */
    @Query("""
        SELECT COALESCE(SUM(f.valor), 0) FROM Fatura f
        WHERE f.status = com.mydelivery.admin.modulos.faturamento.entity.Fatura.Status.VENCIDA
        """)
    BigDecimal somaInadimplencia();

    /** Soma das PAGAS num período (faturamento realizado). */
    @Query("""
        SELECT COALESCE(SUM(f.valor), 0) FROM Fatura f
        WHERE f.status = com.mydelivery.admin.modulos.faturamento.entity.Fatura.Status.PAGA
          AND f.pagamentoEm >= :inicio AND f.pagamentoEm < :fimExcl
        """)
    BigDecimal somaPagasEntre(@Param("inicio") java.time.LocalDateTime inicio,
                              @Param("fimExcl") java.time.LocalDateTime fimExcl);

    long countByStatus(Fatura.Status status);

    /** Faturas vencidas há mais de N dias por restaurante — pra futuro auto-bloqueio. */
    @Query("""
        SELECT f FROM Fatura f
        WHERE f.status = com.mydelivery.admin.modulos.faturamento.entity.Fatura.Status.VENCIDA
          AND f.vencimentoEm <= :limite
          AND f.restauranteId = :restauranteId
        """)
    List<Fatura> findVencidasDeRestauranteAnteAUm(@Param("restauranteId") Long restauranteId,
                                                  @Param("limite") LocalDate limite);

    /** IDs distintos de restaurantes com faturas VENCIDAs com vencimento <= limite. */
    @Query("""
        SELECT DISTINCT f.restauranteId FROM Fatura f
        WHERE f.status = com.mydelivery.admin.modulos.faturamento.entity.Fatura.Status.VENCIDA
          AND f.vencimentoEm <= :limite
        """)
    List<Long> findRestaurantesComVencidasAnteAUm(@Param("limite") LocalDate limite);
}
