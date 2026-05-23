package com.mydelivery.admin.modulos.autocorrecao.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mydelivery.admin.modulos.autocorrecao.service.RecoveryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Roda a engine de auto-correção em intervalos curtos.
 *
 * Default: 60s (cada minuto pega o que tá pronto pra rodar). Em prod com
 * pouca carga, isso é ok; se a fila crescer, o BATCH_SIZE absorve.
 *
 * Override via env:
 *  ADMIN_RECOVERY_FIXED_DELAY_MS=60000
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecoveryScheduler {

    private final RecoveryService recoveryService;

    @Scheduled(
            initialDelayString = "${admin.recovery.initial-delay-ms:45000}",
            fixedDelayString = "${admin.recovery.fixed-delay-ms:60000}"
    )
    public void processar() {
        try {
            int n = recoveryService.processarLote();
            if (n > 0) log.info("[RecoveryScheduler] processou {} ações", n);
        } catch (Exception e) {
            log.error("[RecoveryScheduler] falha no lote: {}", e.getMessage(), e);
        }
    }
}
