package com.mydelivery.admin.shared.main.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mydelivery.admin.shared.main.entity.RestauranteMain;

/**
 * Repository de leitura dos restaurantes — bate no DB principal do MyDelivery.
 *
 * Suporta busca paginada com filtro de status e texto (nome/slug/telefone).
 */
public interface RestauranteMainRepository extends JpaRepository<RestauranteMain, Long> {

    Optional<RestauranteMain> findBySlugIgnoreCase(String slug);

    @Query("""
        SELECT r FROM RestauranteMain r
        WHERE (:status IS NULL OR r.status = :status)
          AND (:q IS NULL OR :q = ''
               OR LOWER(r.nome) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(r.slug) LIKE LOWER(CONCAT('%', :q, '%'))
               OR r.telefone LIKE CONCAT('%', :q, '%'))
        ORDER BY r.criadoEm DESC
        """)
    Page<RestauranteMain> buscar(
            @Param("status") RestauranteMain.Status status,
            @Param("q") String q,
            Pageable pageable);

    long countByStatus(RestauranteMain.Status status);

    /** Lista todos os restaurantes em um conjunto de status — pro monitor varrer. */
    @Query("SELECT r FROM RestauranteMain r WHERE r.status IN :statuses")
    List<RestauranteMain> findByStatusIn(@Param("statuses") List<RestauranteMain.Status> statuses);

    /** Quantos restaurantes foram criados em um intervalo. */
    long countByCriadoEmBetween(java.time.LocalDateTime inicio, java.time.LocalDateTime fim);

    /** Quantos restaurantes foram bloqueados em um intervalo (churn). */
    long countByBloqueadoEmBetween(java.time.LocalDateTime inicio, java.time.LocalDateTime fim);
}
