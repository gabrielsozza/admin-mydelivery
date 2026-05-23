package com.mydelivery.admin.shared.main.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mydelivery.admin.shared.main.entity.SuporteTicketMain;

public interface SuporteTicketMainRepository extends JpaRepository<SuporteTicketMain, Long> {

    /**
     * Busca paginada com filtros opcionais. Qualquer parâmetro null vira "ignora".
     *
     * Atenção: filtros usam o enum/string DO BANCO PRINCIPAL (SuporteTicketMain.Status etc),
     * não o do admin. A conversão Admin↔Main acontece no service antes de chamar.
     */
    @Query("""
            SELECT t FROM SuporteTicketMain t
            WHERE (:status IS NULL OR t.status = :status)
              AND (:prioridade IS NULL OR t.prioridade = :prioridade)
              AND (:categoria IS NULL OR LOWER(t.categoria) = LOWER(:categoria))
              AND (:atendenteId IS NULL OR t.atendenteId = :atendenteId)
              AND (:restauranteId IS NULL OR t.restauranteId = :restauranteId)
              AND (:q IS NULL OR LOWER(t.assunto) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY t.atualizadoEm DESC
            """)
    Page<SuporteTicketMain> buscar(
            @Param("status") SuporteTicketMain.Status status,
            @Param("prioridade") SuporteTicketMain.Prioridade prioridade,
            @Param("categoria") String categoria,
            @Param("atendenteId") Long atendenteId,
            @Param("restauranteId") Long restauranteId,
            @Param("q") String q,
            Pageable pageable);

    /** Total de tickets ainda não resolvidos — pra badge/KPI do dashboard admin. */
    @Query("SELECT COUNT(t) FROM SuporteTicketMain t WHERE t.status IN " +
           "(com.mydelivery.admin.shared.main.entity.SuporteTicketMain.Status.AGUARDANDO, " +
           " com.mydelivery.admin.shared.main.entity.SuporteTicketMain.Status.EM_ATENDIMENTO)")
    long countAbertos();
}
