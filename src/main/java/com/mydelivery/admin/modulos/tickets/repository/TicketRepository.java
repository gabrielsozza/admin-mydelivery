package com.mydelivery.admin.modulos.tickets.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mydelivery.admin.modulos.tickets.entity.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    /**
     * Busca paginada com filtros.
     *  - status, prioridade, categoria, atribuidoA, restauranteId opcionais
     *  - q: busca parcial em titulo/descricao
     *
     * Ordenação default: ABERTOS primeiro, depois por prioridade desc, depois por mais recente.
     * (Quem chama controla via Pageable.)
     */
    @Query("""
        SELECT t FROM Ticket t
        WHERE (:status IS NULL OR t.status = :status)
          AND (:prioridade IS NULL OR t.prioridade = :prioridade)
          AND (:categoria IS NULL OR t.categoria = :categoria)
          AND (:atribuidoA IS NULL OR t.atribuidoA = :atribuidoA)
          AND (:restauranteId IS NULL OR t.restauranteId = :restauranteId)
          AND (:q IS NULL OR :q = ''
               OR LOWER(t.titulo) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(t.descricao) LIKE LOWER(CONCAT('%', :q, '%')))
        """)
    Page<Ticket> buscar(@Param("status") Ticket.Status status,
                        @Param("prioridade") Ticket.Prioridade prioridade,
                        @Param("categoria") Ticket.Categoria categoria,
                        @Param("atribuidoA") Long atribuidoA,
                        @Param("restauranteId") Long restauranteId,
                        @Param("q") String q,
                        Pageable pageable);

    /** Quantos tickets abertos/em andamento existem (KPI do dashboard). */
    @Query("""
        SELECT COUNT(t) FROM Ticket t
        WHERE t.status IN (com.mydelivery.admin.modulos.tickets.entity.Ticket.Status.ABERTO,
                           com.mydelivery.admin.modulos.tickets.entity.Ticket.Status.EM_ANDAMENTO,
                           com.mydelivery.admin.modulos.tickets.entity.Ticket.Status.AGUARDANDO_CLIENTE)
        """)
    long countAbertos();
}
