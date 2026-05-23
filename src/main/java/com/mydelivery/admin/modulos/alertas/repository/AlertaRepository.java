package com.mydelivery.admin.modulos.alertas.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mydelivery.admin.modulos.alertas.entity.Alerta;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    /** Pega o alerta ATIVO que casa com a chave de dedup, se houver. */
    Optional<Alerta> findFirstByDedupKeyAndStatus(String dedupKey, Alerta.Status status);

    /** Todos os ATIVOS de um restaurante. */
    List<Alerta> findByRestauranteIdAndStatusOrderByCriadoEmDesc(Long restauranteId, Alerta.Status status);

    /** Todos os ATIVOS no momento (usado pelo monitor pra auto-resolver). */
    List<Alerta> findByStatus(Alerta.Status status);

    @Query("""
        SELECT a FROM Alerta a
        WHERE (:status IS NULL OR a.status = :status)
          AND (:severidade IS NULL OR a.severidade = :severidade)
          AND (:tipo IS NULL OR a.tipo = :tipo)
          AND (:restauranteId IS NULL OR a.restauranteId = :restauranteId)
        """)
    Page<Alerta> buscar(@Param("status") Alerta.Status status,
                        @Param("severidade") Alerta.Severidade severidade,
                        @Param("tipo") Alerta.Tipo tipo,
                        @Param("restauranteId") Long restauranteId,
                        Pageable pageable);

    @Query("SELECT COUNT(a) FROM Alerta a WHERE a.status = com.mydelivery.admin.modulos.alertas.entity.Alerta.Status.ATIVO")
    long countAtivos();

    @Query("""
        SELECT COUNT(a) FROM Alerta a
        WHERE a.status = com.mydelivery.admin.modulos.alertas.entity.Alerta.Status.ATIVO
          AND a.severidade IN (com.mydelivery.admin.modulos.alertas.entity.Alerta.Severidade.ALTA,
                               com.mydelivery.admin.modulos.alertas.entity.Alerta.Severidade.CRITICA)
        """)
    long countAtivosAltos();
}
