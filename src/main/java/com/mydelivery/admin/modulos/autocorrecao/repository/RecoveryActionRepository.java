package com.mydelivery.admin.modulos.autocorrecao.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mydelivery.admin.modulos.autocorrecao.entity.RecoveryAction;

public interface RecoveryActionRepository extends JpaRepository<RecoveryAction, Long> {

    /** Pega uma RecoveryAction "em vida" com o mesmo dedupKey. */
    @Query("""
        SELECT r FROM RecoveryAction r
        WHERE r.dedupKey = :dedupKey
          AND r.status IN (com.mydelivery.admin.modulos.autocorrecao.entity.RecoveryAction.Status.PENDENTE,
                           com.mydelivery.admin.modulos.autocorrecao.entity.RecoveryAction.Status.EXECUTANDO,
                           com.mydelivery.admin.modulos.autocorrecao.entity.RecoveryAction.Status.AGUARDANDO_RETRY)
        """)
    Optional<RecoveryAction> findAtivaByDedupKey(@Param("dedupKey") String dedupKey);

    /**
     * Lista de ações prontas pra rodar (PENDENTE ou AGUARDANDO_RETRY cujo próximo
     * agendamento já chegou). Limitado pra não estourar memória.
     */
    @Query("""
        SELECT r FROM RecoveryAction r
        WHERE r.status IN (com.mydelivery.admin.modulos.autocorrecao.entity.RecoveryAction.Status.PENDENTE,
                           com.mydelivery.admin.modulos.autocorrecao.entity.RecoveryAction.Status.AGUARDANDO_RETRY)
          AND r.proximaTentativaEm <= :agora
        ORDER BY r.proximaTentativaEm ASC
        """)
    List<RecoveryAction> findProntasParaExecutar(@Param("agora") LocalDateTime agora, Pageable pageable);

    @Query("""
        SELECT r FROM RecoveryAction r
        WHERE (:status IS NULL OR r.status = :status)
          AND (:tipo IS NULL OR r.tipo = :tipo)
          AND (:restauranteId IS NULL OR r.restauranteId = :restauranteId)
        """)
    Page<RecoveryAction> buscar(@Param("status") RecoveryAction.Status status,
                                @Param("tipo") RecoveryAction.Tipo tipo,
                                @Param("restauranteId") Long restauranteId,
                                Pageable pageable);

    @Query("""
        SELECT COUNT(r) FROM RecoveryAction r
        WHERE r.status IN (com.mydelivery.admin.modulos.autocorrecao.entity.RecoveryAction.Status.PENDENTE,
                           com.mydelivery.admin.modulos.autocorrecao.entity.RecoveryAction.Status.EXECUTANDO,
                           com.mydelivery.admin.modulos.autocorrecao.entity.RecoveryAction.Status.AGUARDANDO_RETRY)
        """)
    long countEmAndamento();

    long countByStatusAndCriadoEmAfter(RecoveryAction.Status status, LocalDateTime momento);
}
