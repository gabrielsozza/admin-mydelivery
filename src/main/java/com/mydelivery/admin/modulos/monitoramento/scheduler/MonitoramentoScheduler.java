package com.mydelivery.admin.modulos.monitoramento.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mydelivery.admin.modulos.monitoramento.service.MonitoramentoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Roda o monitor em intervalos fixos.
 *
 * Intervalos default:
 *  - ciclo de health: a cada 5 minutos
 *  - purge de snapshots antigos: 1x por dia, 03h
 *
 * Tudo overridable via env:
 *  ADMIN_MONITOR_CICLO_MS = 300000  (em ms)
 *  ADMIN_MONITOR_PURGE_CRON = "0 0 3 * * *"
 *
 * Pra desligar o scheduler em dev: spring.task.scheduling.enabled=false
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonitoramentoScheduler {

    private final MonitoramentoService monitoramentoService;

    /** Roda 30s depois do boot, e depois a cada N ms (default 5min). */
    @Scheduled(
            initialDelayString = "${admin.monitor.ciclo.initial-delay-ms:30000}",
            fixedDelayString = "${admin.monitor.ciclo.fixed-delay-ms:300000}"
    )
    public void cicloHealthCheck() {
        try {
            monitoramentoService.rodarCiclo();
        } catch (Exception e) {
            log.error("[MonitorScheduler] falha no ciclo: {}", e.getMessage(), e);
        }
    }

    /** Purge diário às 3h da manhã. */
    @Scheduled(cron = "${admin.monitor.purge.cron:0 0 3 * * *}")
    public void purgeSnapshots() {
        try {
            monitoramentoService.purgarSnapshotsAntigos();
        } catch (Exception e) {
            log.error("[MonitorScheduler] falha no purge: {}", e.getMessage(), e);
        }
    }

    /** Lido pelo Spring só pra logar (não muda comportamento). */
    @Value("${admin.monitor.ciclo.fixed-delay-ms:300000}")
    private long fixedDelayMs;
}
