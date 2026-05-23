package com.mydelivery.admin.modulos.monitoramento.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Foto pontual da saúde de um restaurante.
 *
 * Salvo a cada ciclo do {@link com.mydelivery.admin.modulos.monitoramento.scheduler.MonitoramentoScheduler}.
 * Permite histórico e gráficos de uptime. Mantenha esta tabela pequena: o
 * scheduler também faz purge dos snapshots antigos (> 30 dias).
 */
@Entity
@Table(name = "health_snapshot", indexes = {
        @Index(name = "idx_snap_restaurante", columnList = "restaurante_id"),
        @Index(name = "idx_snap_capturado", columnList = "capturado_em")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurante_id", nullable = false)
    private Long restauranteId;

    @Column(name = "capturado_em", nullable = false)
    private LocalDateTime capturadoEm;

    /** Status no DB main no momento do check. */
    @Column(name = "status_restaurante", length = 30)
    private String statusRestaurante;

    /** O restaurante está com switch "aberto" ligado? */
    @Column(name = "aberto", nullable = false)
    private Boolean aberto;

    /** Está dentro do horário comercial padrão (06h-23h Sao_Paulo)? */
    @Column(name = "dentro_horario", nullable = false)
    private Boolean dentroHorario;

    /** 0-100. 100 = perfeito, 0 = crítico. */
    @Column(name = "score", nullable = false)
    private Integer score;

    /** Lista de chaves de problemas detectados, vírgula-separada. */
    @Column(name = "problemas", length = 500)
    private String problemas;

    @PrePersist
    void prePersist() {
        if (capturadoEm == null) capturadoEm = LocalDateTime.now();
        if (score == null) score = 100;
        if (aberto == null) aberto = false;
        if (dentroHorario == null) dentroHorario = false;
    }
}
