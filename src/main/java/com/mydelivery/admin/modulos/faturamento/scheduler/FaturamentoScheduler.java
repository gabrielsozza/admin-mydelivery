package com.mydelivery.admin.modulos.faturamento.scheduler;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mydelivery.admin.modulos.faturamento.service.FaturaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Schedulers do faturamento.
 *
 *  1. Gerar faturas mensais — dia 1 de cada mês 06:00 (Sao_Paulo)
 *  2. Marcar PENDENTES vencidas como VENCIDA — diariamente 02:00
 *  3. Detectar inadimplência crítica → solicitar bloqueio — diariamente 02:30
 *
 * Cron format: segundo minuto hora dia mês dia-semana (6 campos do Spring).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FaturamentoScheduler {

    private final FaturaService faturaService;

    @Value("${admin.inadimplencia.dias-tolerancia:7}")
    private int diasTolerancia;

    /** Roda dia 1 às 06:00. Gera faturas referentes ao mês corrente. */
    @Scheduled(cron = "${admin.faturamento.gerar.cron:0 0 6 1 * *}")
    public void gerarFaturasMensais() {
        try {
            FaturaService.GeracaoResumo r = faturaService.gerarFaturasDaCompetencia(LocalDate.now());
            log.info("[FaturamentoScheduler] geração mensal — competência={} criadas={} puladas={}",
                    r.competencia(), r.faturasCriadas(), r.faturasPuladas());
        } catch (Exception e) {
            log.error("[FaturamentoScheduler] falha na geração mensal: {}", e.getMessage(), e);
        }
    }

    /** Diário às 02:00 — marca PENDENTES com vencimento passado como VENCIDA. */
    @Scheduled(cron = "${admin.faturamento.vencer.cron:0 0 2 * * *}")
    public void marcarVencidas() {
        try {
            int n = faturaService.marcarVencidas();
            if (n > 0) log.info("[FaturamentoScheduler] {} faturas marcadas como VENCIDA", n);
        } catch (Exception e) {
            log.error("[FaturamentoScheduler] falha ao marcar vencidas: {}", e.getMessage(), e);
        }
    }

    /**
     * Diário às 02:30 — pra cada restaurante com fatura VENCIDA há > diasTolerancia,
     * solicita bloqueio automático (Recovery engine cuida do resto).
     *
     * Roda 30 min depois do marcarVencidas pra dar tempo da janela diária estabilizar.
     */
    @Scheduled(cron = "${admin.faturamento.inadimplencia.cron:0 30 2 * * *}")
    public void detectarInadimplencia() {
        try {
            int n = faturaService.detectarInadimplenciaCriticaEAcionarBloqueio(diasTolerancia);
            if (n > 0) log.info("[FaturamentoScheduler] {} restaurantes solicitados pra bloqueio por inadimplência", n);
        } catch (Exception e) {
            log.error("[FaturamentoScheduler] falha na detecção de inadimplência: {}", e.getMessage(), e);
        }
    }
}
