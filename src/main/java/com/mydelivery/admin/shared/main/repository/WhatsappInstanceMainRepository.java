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

    /** Instâncias problemáticas pra alerta no dashboard: DESCONECTADA + ERRO. */
    @Query("""
        SELECT w FROM WhatsappInstanceMain w
        WHERE w.status IN (com.mydelivery.admin.shared.main.entity.WhatsappInstanceMain.Status.DESCONECTADA,
                            com.mydelivery.admin.shared.main.entity.WhatsappInstanceMain.Status.ERRO)
        ORDER BY w.atualizadoEm DESC
        """)
    List<WhatsappInstanceMain> findProblematicas(Pageable pageable);
}
