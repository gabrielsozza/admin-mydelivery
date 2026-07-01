package com.mydelivery.admin.shared.main.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mydelivery.admin.shared.main.entity.WhatsappInstanceMain;

public interface WhatsappInstanceMainRepository extends JpaRepository<WhatsappInstanceMain, Long> {

    Optional<WhatsappInstanceMain> findByRestauranteId(Long restauranteId);

    @Query("""
        SELECT w FROM WhatsappInstanceMain w
        WHERE (:status IS NULL OR w.status = :status)
        ORDER BY w.atualizadoEm DESC
        """)
    Page<WhatsappInstanceMain> buscar(@Param("status") WhatsappInstanceMain.Status status, Pageable pageable);

    long countByStatus(WhatsappInstanceMain.Status status);

    /** DESCONECTADAS por queda inesperada (NÃO conta as manuais). */
    @Query("""
        SELECT COUNT(w) FROM WhatsappInstanceMain w
        WHERE w.status = com.mydelivery.admin.shared.main.entity.WhatsappInstanceMain.Status.DESCONECTADA
          AND (w.desconectadoManualmente IS NULL OR w.desconectadoManualmente = false)
        """)
    long countDesconectadasInesperadas();

    /** DESCONECTADAS pelo próprio dono (fluxo normal — informativo, não alerta). */
    @Query("""
        SELECT COUNT(w) FROM WhatsappInstanceMain w
        WHERE w.status = com.mydelivery.admin.shared.main.entity.WhatsappInstanceMain.Status.DESCONECTADA
          AND w.desconectadoManualmente = true
        """)
    long countDesconectadasManuais();

    /** Instâncias problemáticas pra alerta no dashboard: DESCONECTADA INESPERADA + ERRO.
     *  Importante: EXCLUI desconectados manualmente (não é problema). */
    @Query("""
        SELECT w FROM WhatsappInstanceMain w
        WHERE (w.status = com.mydelivery.admin.shared.main.entity.WhatsappInstanceMain.Status.DESCONECTADA
                AND (w.desconectadoManualmente IS NULL OR w.desconectadoManualmente = false))
           OR w.status = com.mydelivery.admin.shared.main.entity.WhatsappInstanceMain.Status.ERRO
        ORDER BY w.atualizadoEm DESC
        """)
    List<WhatsappInstanceMain> findProblematicas(Pageable pageable);
}
