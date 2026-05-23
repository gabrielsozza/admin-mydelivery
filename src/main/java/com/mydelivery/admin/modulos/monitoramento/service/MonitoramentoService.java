package com.mydelivery.admin.modulos.monitoramento.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mydelivery.admin.modulos.alertas.entity.Alerta;
import com.mydelivery.admin.modulos.alertas.service.AlertaService;
import com.mydelivery.admin.modulos.autocorrecao.entity.RecoveryAction;
import com.mydelivery.admin.modulos.autocorrecao.service.RecoveryService;
import com.mydelivery.admin.modulos.monitoramento.entity.HealthSnapshot;
import com.mydelivery.admin.modulos.monitoramento.repository.HealthSnapshotRepository;
import com.mydelivery.admin.shared.main.entity.RestauranteMain;
import com.mydelivery.admin.shared.main.repository.RestauranteMainRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Engine de monitoramento. Pra cada restaurante ATIVO/TRIAL:
 *
 *   1. captura snapshot com status atual
 *   2. detecta problemas conhecidos
 *   3. emite alertas (com dedup) ou auto-resolve se sumiu
 *
 * Cabe num único método público — {@link #rodarCiclo()} — chamado pelo scheduler.
 * Também dá pra disparar manualmente via endpoint pra teste/debug.
 *
 * Regras de problema implementadas:
 *  - TRIAL_EXPIRANDO   → trial_expira_em em até 3 dias            (vira alerta direto)
 *  - TRIAL_EXPIRADO    → status=TRIAL e trial_expira_em < agora   (vira RecoveryAction)
 *  - RESTAURANTE_FECHADO_INESPERADO → aberto=false durante 06h-23h (vira alerta direto)
 *
 * Pra problemas que têm auto-correção, o monitor chama RecoveryService em vez de
 * alertaService — o alerta só sai se a auto-correção falhar todas as tentativas.
 *
 * Futuro: WHATSAPP_DESCONECTADO, RESTAURANTE_SEM_PEDIDOS (precisa de mirror de
 * pedidos do main DB).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitoramentoService {

    /** Horário comercial padrão (Sao_Paulo). Fora disso, aberto=false não vira alerta. */
    private static final LocalTime HORA_ABRE = LocalTime.of(6, 0);
    private static final LocalTime HORA_FECHA = LocalTime.of(23, 0);

    /** Janela "trial expirando" — alerta MEDIA até esse prazo antes de expirar. */
    private static final Duration JANELA_TRIAL_EXPIRANDO = Duration.ofDays(3);

    /** Quantos dias guardar de snapshots. */
    private static final int DIAS_RETENCAO_SNAPSHOT = 30;

    private final RestauranteMainRepository restauranteRepo;
    private final HealthSnapshotRepository snapshotRepo;
    private final AlertaService alertaService;
    private final RecoveryService recoveryService;

    /**
     * Executa um ciclo completo de monitoramento.
     * @return resumo: quantos checados / alertas novos / auto-resolvidos
     */
    @Transactional
    public CicloResumo rodarCiclo() {
        log.info("[Monitor] ciclo iniciado");
        LocalDateTime agora = LocalDateTime.now();

        List<RestauranteMain> alvos = restauranteRepo.findByStatusIn(List.of(
                RestauranteMain.Status.ATIVO,
                RestauranteMain.Status.TRIAL
        ));

        // Pra cada tipo monitorado, vamos coletar os dedupKeys que AINDA estão acontecendo.
        // No fim, qualquer alerta ATIVO de mesmo tipo que NÃO esteja nessa lista vira RESOLVIDO.
        List<String> trialExpirandoVivos = new ArrayList<>();
        List<String> trialExpiradoVivos = new ArrayList<>();
        List<String> fechadoInesperadoVivos = new ArrayList<>();

        int novos = 0;

        for (RestauranteMain r : alvos) {
            List<String> problemas = new ArrayList<>();
            int score = 100;

            boolean dentroHorario = dentroHorarioComercial(agora);

            // 1) TRIAL_EXPIRADO → auto-correção tenta bloquear automaticamente.
            //    Alerta só é emitido se a recovery falhar (RecoveryService decide).
            if (r.getStatus() == RestauranteMain.Status.TRIAL
                    && r.getTrialExpiraEm() != null
                    && r.getTrialExpiraEm().isBefore(agora)) {
                problemas.add("TRIAL_EXPIRADO");
                score -= 50;
                trialExpiradoVivos.add(Alerta.Tipo.TRIAL_EXPIRADO.name() + ":" + r.getId());
                recoveryService.solicitar(
                        RecoveryAction.Tipo.BLOQUEAR_TRIAL_EXPIRADO,
                        r.getId(),
                        "{\"trialExpiraEm\":\"" + r.getTrialExpiraEm() + "\"}",
                        "MONITOR"
                );
                novos++;
            }
            // 2) TRIAL_EXPIRANDO (só se ainda não expirou)
            else if (r.getStatus() == RestauranteMain.Status.TRIAL
                    && r.getTrialExpiraEm() != null
                    && r.getTrialExpiraEm().isAfter(agora)
                    && Duration.between(agora, r.getTrialExpiraEm()).compareTo(JANELA_TRIAL_EXPIRANDO) < 0) {
                problemas.add("TRIAL_EXPIRANDO");
                score -= 15;
                trialExpirandoVivos.add(Alerta.Tipo.TRIAL_EXPIRANDO.name() + ":" + r.getId());
                long horasRestantes = Duration.between(agora, r.getTrialExpiraEm()).toHours();
                alertaService.emitir(
                        Alerta.Tipo.TRIAL_EXPIRANDO,
                        Alerta.Severidade.MEDIA,
                        r.getId(),
                        "Trial expirando: " + r.getNome(),
                        "Faltam ~" + horasRestantes + "h pro trial acabar (" + r.getTrialExpiraEm() + ").",
                        null,
                        null
                );
                novos++;
            }

            // 3) RESTAURANTE_FECHADO_INESPERADO — só se status ATIVO e dentro do horário
            if (r.getStatus() == RestauranteMain.Status.ATIVO
                    && dentroHorario
                    && Boolean.FALSE.equals(r.getAberto())) {
                problemas.add("FECHADO_INESPERADO");
                score -= 25;
                fechadoInesperadoVivos.add(Alerta.Tipo.RESTAURANTE_FECHADO_INESPERADO.name() + ":" + r.getId());
                alertaService.emitir(
                        Alerta.Tipo.RESTAURANTE_FECHADO_INESPERADO,
                        Alerta.Severidade.MEDIA,
                        r.getId(),
                        "Restaurante fechado em horário comercial: " + r.getNome(),
                        "O switch 'aberto' está desligado, mas é " + agora.toLocalTime()
                                + " (dentro do horário " + HORA_ABRE + "-" + HORA_FECHA + ").",
                        null,
                        null
                );
                novos++;
            }

            if (score < 0) score = 0;

            HealthSnapshot snap = HealthSnapshot.builder()
                    .restauranteId(r.getId())
                    .capturadoEm(agora)
                    .statusRestaurante(r.getStatus() == null ? null : r.getStatus().name())
                    .aberto(Boolean.TRUE.equals(r.getAberto()))
                    .dentroHorario(dentroHorario)
                    .score(score)
                    .problemas(String.join(",", problemas))
                    .build();
            snapshotRepo.save(snap);
        }

        // Auto-resolve: alertas ATIVOS desses tipos que não estão mais entre os vivos
        int autoResolvidos = 0;
        autoResolvidos += alertaService.resolverAutomaticamente(trialExpiradoVivos, Alerta.Tipo.TRIAL_EXPIRADO);
        autoResolvidos += alertaService.resolverAutomaticamente(trialExpirandoVivos, Alerta.Tipo.TRIAL_EXPIRANDO);
        autoResolvidos += alertaService.resolverAutomaticamente(fechadoInesperadoVivos, Alerta.Tipo.RESTAURANTE_FECHADO_INESPERADO);

        log.info("[Monitor] ciclo ok — checados={} novosOuRepetidos={} autoResolvidos={}",
                alvos.size(), novos, autoResolvidos);
        return new CicloResumo(alvos.size(), novos, autoResolvidos);
    }

    /**
     * Purge snapshots antigos. Roda separado pra não atrapalhar a transação do ciclo.
     */
    @Transactional
    public int purgarSnapshotsAntigos() {
        LocalDateTime limite = LocalDateTime.now().minusDays(DIAS_RETENCAO_SNAPSHOT);
        int n = snapshotRepo.deletarAntigos(limite);
        if (n > 0) log.info("[Monitor] snapshots antigos removidos: {}", n);
        return n;
    }

    /** Snapshot mais recente de um restaurante. */
    @Transactional(readOnly = true)
    public HealthSnapshot ultimoSnapshot(Long restauranteId) {
        return snapshotRepo.findFirstByRestauranteIdOrderByCapturadoEmDesc(restauranteId).orElse(null);
    }

    /** Histórico recente (até 50). */
    @Transactional(readOnly = true)
    public List<HealthSnapshot> historico(Long restauranteId) {
        return snapshotRepo.findTop50ByRestauranteIdOrderByCapturadoEmDesc(restauranteId);
    }

    // ─── HELPERS ───────────────────────────────────────────────────────────

    private static boolean dentroHorarioComercial(LocalDateTime momento) {
        LocalTime t = momento.toLocalTime();
        return !t.isBefore(HORA_ABRE) && t.isBefore(HORA_FECHA);
    }

    /** Retorno do {@link #rodarCiclo()}. */
    public record CicloResumo(int restaurantesChecados, int alertasEmitidos, int alertasAutoResolvidos) {}
}
