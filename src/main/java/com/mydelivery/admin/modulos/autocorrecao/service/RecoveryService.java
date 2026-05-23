package com.mydelivery.admin.modulos.autocorrecao.service;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mydelivery.admin.modulos.alertas.entity.Alerta;
import com.mydelivery.admin.modulos.alertas.service.AlertaService;
import com.mydelivery.admin.modulos.autocorrecao.dto.RecoveryActionDetalheDTO;
import com.mydelivery.admin.modulos.autocorrecao.dto.RecoveryActionListDTO;
import com.mydelivery.admin.modulos.autocorrecao.entity.RecoveryAction;
import com.mydelivery.admin.modulos.autocorrecao.executor.RecoveryExecutor;
import com.mydelivery.admin.modulos.autocorrecao.executor.RecoveryResult;
import com.mydelivery.admin.modulos.autocorrecao.repository.RecoveryActionRepository;
import com.mydelivery.admin.shared.exception.NotFoundException;
import com.mydelivery.admin.shared.main.entity.RestauranteMain;
import com.mydelivery.admin.shared.main.repository.RestauranteMainRepository;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Orquestrador da engine de auto-correção.
 *
 *  - {@link #solicitar} cria a ação (com dedup) ou retorna a existente
 *  - {@link #processarLote} é chamado pelo scheduler periodicamente
 *  - {@link #executarUma} faz uma tentativa (extraído pra ficar testável)
 *
 * Mapeia tipo → executor no construtor (DI traz a lista de executors registrados).
 *
 * Política de retry: backoff 1min, 5min, 15min (3 tentativas default).
 */
@Slf4j
@Service
public class RecoveryService {

    /** Quantas ações o scheduler pega por ciclo. */
    private static final int BATCH_SIZE = 20;

    private final RecoveryActionRepository repo;
    private final AlertaService alertaService;
    private final RestauranteMainRepository restauranteRepo;
    private final List<RecoveryExecutor> executors;
    private final Map<RecoveryAction.Tipo, RecoveryExecutor> executorPorTipo = new EnumMap<>(RecoveryAction.Tipo.class);

    public RecoveryService(RecoveryActionRepository repo,
                           AlertaService alertaService,
                           RestauranteMainRepository restauranteRepo,
                           List<RecoveryExecutor> executors) {
        this.repo = repo;
        this.alertaService = alertaService;
        this.restauranteRepo = restauranteRepo;
        this.executors = executors;
    }

    @PostConstruct
    void registrarExecutors() {
        for (RecoveryExecutor ex : executors) {
            RecoveryExecutor prev = executorPorTipo.put(ex.tipo(), ex);
            if (prev != null) {
                throw new IllegalStateException(
                        "Dois executors pro mesmo tipo " + ex.tipo() + ": "
                                + prev.getClass().getSimpleName() + " e " + ex.getClass().getSimpleName());
            }
        }
        log.info("[Recovery] executors registrados: {}", executorPorTipo.keySet());
    }

    // ─── SOLICITAR ─────────────────────────────────────────────────────────

    /**
     * Cria uma RecoveryAction PENDENTE (ou retorna a existente, com dedup).
     *
     * @param tipo           qual ação tentar
     * @param restauranteId  alvo
     * @param payloadJson    contexto extra (opcional)
     * @param solicitadoPor  "MONITOR" se foi auto, ou ID do admin
     */
    @Transactional
    public RecoveryAction solicitar(RecoveryAction.Tipo tipo,
                                    Long restauranteId,
                                    String payloadJson,
                                    String solicitadoPor) {
        String dedupKey = tipo.name() + ":" + restauranteId;

        return repo.findAtivaByDedupKey(dedupKey)
                .map(ja -> {
                    log.debug("[Recovery] solicitação ignorada — já existe ação {} em vida ({})",
                            ja.getId(), ja.getStatus());
                    return ja;
                })
                .orElseGet(() -> {
                    RecoveryAction nova = RecoveryAction.builder()
                            .tipo(tipo)
                            .restauranteId(restauranteId)
                            .dedupKey(dedupKey)
                            .payload(payloadJson)
                            .solicitadoPor(solicitadoPor != null ? solicitadoPor : "MONITOR")
                            .status(RecoveryAction.Status.PENDENTE)
                            .tentativas(0)
                            .maxTentativas(3)
                            .proximaTentativaEm(LocalDateTime.now())
                            .build();
                    log.info("[Recovery] criada {} restauranteId={}", tipo, restauranteId);
                    return repo.save(nova);
                });
    }

    // ─── PROCESSAR ─────────────────────────────────────────────────────────

    /**
     * Pega até {@code BATCH_SIZE} ações prontas e roda. Chamado pelo scheduler.
     * Retorna quantas foram processadas (qualquer resultado conta).
     */
    @Transactional
    public int processarLote() {
        Pageable limite = PageRequest.of(0, BATCH_SIZE, Sort.by("proximaTentativaEm"));
        List<RecoveryAction> prontas = repo.findProntasParaExecutar(LocalDateTime.now(), limite);
        if (prontas.isEmpty()) return 0;

        log.info("[Recovery] processando {} ações", prontas.size());
        int processadas = 0;
        for (RecoveryAction action : prontas) {
            try {
                executarUma(action);
                processadas++;
            } catch (Exception e) {
                log.error("[Recovery] exception inesperada na ação {}: {}",
                        action.getId(), e.getMessage(), e);
                marcarFalha(action, "Exception não capturada: " + e.getMessage());
            }
        }
        return processadas;
    }

    /**
     * Executa UMA RecoveryAction. Faz transição de estado de acordo com o resultado.
     * Public pra ser invocável via endpoint manual também.
     */
    @Transactional
    public RecoveryAction executarUma(RecoveryAction action) {
        RecoveryExecutor exec = executorPorTipo.get(action.getTipo());
        if (exec == null) {
            return marcarFalha(action, "Sem executor pro tipo " + action.getTipo());
        }

        action.setStatus(RecoveryAction.Status.EXECUTANDO);
        action.setUltimaTentativaEm(LocalDateTime.now());
        action.setTentativas(action.getTentativas() + 1);
        repo.save(action);

        RecoveryResult result;
        try {
            result = exec.executar(action);
        } catch (Exception e) {
            log.error("[Recovery] executor lançou exception action={} tipo={}: {}",
                    action.getId(), action.getTipo(), e.getMessage(), e);
            result = RecoveryResult.falha("Exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        if (result.ok()) {
            return marcarSucesso(action, result);
        } else {
            return marcarTentativaFalhou(action, result);
        }
    }

    // ─── TRANSIÇÕES DE ESTADO ─────────────────────────────────────────────

    private RecoveryAction marcarSucesso(RecoveryAction a, RecoveryResult r) {
        a.setStatus(RecoveryAction.Status.SUCESSO);
        a.setFinalizadoEm(LocalDateTime.now());
        a.setResultado(jsonResultado(true, r.mensagem(), r.detalheJson()));
        log.info("[Recovery] ✓ SUCESSO action={} tipo={} restauranteId={}",
                a.getId(), a.getTipo(), a.getRestauranteId());
        return repo.save(a);
    }

    private RecoveryAction marcarTentativaFalhou(RecoveryAction a, RecoveryResult r) {
        a.setResultado(jsonResultado(false, r.mensagem(), r.detalheJson()));

        if (a.getTentativas() >= a.getMaxTentativas()) {
            return marcarFalhaDefinitiva(a, r);
        }

        // backoff: 1min, 5min, 15min
        long minutos = switch (a.getTentativas()) {
            case 1 -> 1L;
            case 2 -> 5L;
            default -> 15L;
        };
        a.setStatus(RecoveryAction.Status.AGUARDANDO_RETRY);
        a.setProximaTentativaEm(LocalDateTime.now().plusMinutes(minutos));
        log.warn("[Recovery] ✗ tentativa {}/{} falhou — retry em {}min — action={} motivo={}",
                a.getTentativas(), a.getMaxTentativas(), minutos, a.getId(), r.mensagem());
        return repo.save(a);
    }

    private RecoveryAction marcarFalhaDefinitiva(RecoveryAction a, RecoveryResult r) {
        a.setStatus(RecoveryAction.Status.FALHOU);
        a.setFinalizadoEm(LocalDateTime.now());
        log.error("[Recovery] ✗✗ FALHOU definitivo action={} tipo={} restauranteId={} motivo={}",
                a.getId(), a.getTipo(), a.getRestauranteId(), r.mensagem());

        // Emite alerta agora — a auto-correção desistiu, precisa olho humano.
        // dedupContexto vazio para casar com a dedupKey que o monitor usa pra
        // auto-resolver (TRIAL_EXPIRADO:<restauranteId>). O link inverso fica
        // em a.alertaEmitidoId.
        Alerta alerta = alertaService.emitir(
                tipoAlertaDe(a.getTipo()),
                Alerta.Severidade.ALTA,
                a.getRestauranteId(),
                "Auto-correção falhou: " + a.getTipo(),
                "Recovery action #" + a.getId() + " falhou " + a.getMaxTentativas()
                        + " vezes. Último erro: " + r.mensagem(),
                a.getResultado(),
                null
        );
        a.setAlertaEmitidoId(alerta.getId());
        return repo.save(a);
    }

    /** Falha sem ter passado pelo executor (ex: tipo desconhecido). Sem retry. */
    private RecoveryAction marcarFalha(RecoveryAction a, String motivo) {
        a.setStatus(RecoveryAction.Status.FALHOU);
        a.setFinalizadoEm(LocalDateTime.now());
        a.setResultado(jsonResultado(false, motivo, null));
        log.error("[Recovery] falha estrutural action={}: {}", a.getId(), motivo);
        return repo.save(a);
    }

    // ─── AÇÕES ADMIN ──────────────────────────────────────────────────────

    /** Força nova tentativa imediata (admin clica "tentar agora"). */
    @Transactional
    public RecoveryActionDetalheDTO forcarRetry(Long id) {
        RecoveryAction a = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("RecoveryAction não encontrada"));

        if (a.getStatus() == RecoveryAction.Status.SUCESSO || a.getStatus() == RecoveryAction.Status.CANCELADA) {
            throw new IllegalStateException("Ação já finalizada como " + a.getStatus() + " — crie uma nova");
        }
        if (a.getStatus() == RecoveryAction.Status.EXECUTANDO) {
            throw new IllegalStateException("Ação já está executando");
        }

        // Se já tinha falhado de vez, dá mais 1 chance: reduz o contador
        if (a.getStatus() == RecoveryAction.Status.FALHOU) {
            a.setTentativas(Math.max(0, a.getTentativas() - 1));
            a.setFinalizadoEm(null);
        }
        a.setStatus(RecoveryAction.Status.PENDENTE);
        a.setProximaTentativaEm(LocalDateTime.now());
        repo.save(a);

        executarUma(a);
        return detalhe(id);
    }

    /** Admin cancela manualmente. */
    @Transactional
    public RecoveryActionDetalheDTO cancelar(Long id, String motivo) {
        RecoveryAction a = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("RecoveryAction não encontrada"));

        if (a.getStatus() == RecoveryAction.Status.SUCESSO || a.getStatus() == RecoveryAction.Status.CANCELADA) {
            throw new IllegalStateException("Ação já finalizada como " + a.getStatus());
        }
        a.setStatus(RecoveryAction.Status.CANCELADA);
        a.setFinalizadoEm(LocalDateTime.now());
        a.setResultado(jsonResultado(false,
                "Cancelada manualmente por " + adminAtual(),
                motivo == null ? null : "{\"motivo\":\"" + motivo.replace("\"", "'") + "\"}"));
        repo.save(a);
        return detalhe(id);
    }

    // ─── LISTAR / DETALHE ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<RecoveryActionListDTO> listar(String statusStr,
                                              String tipoStr,
                                              Long restauranteId,
                                              int page, int size) {
        RecoveryAction.Status status = parseEnum(RecoveryAction.Status.class, statusStr);
        RecoveryAction.Tipo tipo = parseEnum(RecoveryAction.Tipo.class, tipoStr);

        if (size <= 0 || size > 100) size = 25;
        if (page < 0) page = 0;
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "criadoEm"));

        Page<RecoveryAction> acoes = repo.buscar(status, tipo, restauranteId, pageable);
        Map<Long, String> nomes = nomesRestaurantes(acoes.map(RecoveryAction::getRestauranteId).toList());
        return acoes.map(a -> RecoveryActionListDTO.from(a, nomes.get(a.getRestauranteId())));
    }

    @Transactional(readOnly = true)
    public RecoveryActionDetalheDTO detalhe(Long id) {
        RecoveryAction a = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("RecoveryAction não encontrada"));
        String nome = restauranteRepo.findById(a.getRestauranteId())
                .map(RestauranteMain::getNome).orElse(null);
        return RecoveryActionDetalheDTO.from(a, nome);
    }

    // ─── KPIs ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public long countEmAndamento() { return repo.countEmAndamento(); }

    @Transactional(readOnly = true)
    public long countSucessoUltimas24h() {
        return repo.countByStatusAndCriadoEmAfter(
                RecoveryAction.Status.SUCESSO,
                LocalDateTime.now().minusHours(24));
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────

    private static Alerta.Tipo tipoAlertaDe(RecoveryAction.Tipo t) {
        return switch (t) {
            case BLOQUEAR_TRIAL_EXPIRADO -> Alerta.Tipo.TRIAL_EXPIRADO;
            case BLOQUEAR_INADIMPLENTE -> Alerta.Tipo.FATURA_ATRASADA;
        };
    }

    private static String jsonResultado(boolean ok, String mensagem, String detalheJson) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"ok\":").append(ok);
        if (mensagem != null) {
            sb.append(",\"mensagem\":\"")
              .append(mensagem.replace("\\", "\\\\").replace("\"", "\\\""))
              .append("\"");
        }
        if (detalheJson != null && !detalheJson.isBlank()) {
            sb.append(",\"detalhe\":").append(detalheJson);
        }
        sb.append(",\"em\":\"").append(LocalDateTime.now()).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private static String adminAtual() {
        var authn = SecurityContextHolder.getContext().getAuthentication();
        return authn != null && authn.getName() != null ? authn.getName() : "sistema";
    }

    private Map<Long, String> nomesRestaurantes(List<Long> ids) {
        List<Long> distintos = ids.stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (distintos.isEmpty()) return new HashMap<>();
        Map<Long, String> out = new HashMap<>();
        restauranteRepo.findAllById(distintos).forEach(r -> out.put(r.getId(), r.getNome()));
        return out;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Enum.valueOf(type, s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
