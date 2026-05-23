package com.mydelivery.admin.modulos.monitoramento.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mydelivery.admin.modulos.monitoramento.entity.HealthSnapshot;

public interface HealthSnapshotRepository extends JpaRepository<HealthSnapshot, Long> {

    /** Último snapshot de um restaurante. */
    Optional<HealthSnapshot> findFirstByRestauranteIdOrderByCapturadoEmDesc(Long restauranteId);

    /** Histórico recente de um restaurante (gráfico). */
    List<HealthSnapshot> findTop50ByRestauranteIdOrderByCapturadoEmDesc(Long restauranteId);

    /** Purge — apaga snapshots antigos pra tabela não explodir. */
    @Modifying
    @Query("DELETE FROM HealthSnapshot s WHERE s.capturadoEm < :limite")
    int deletarAntigos(@Param("limite") LocalDateTime limite);
}
