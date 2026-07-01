package com.mydelivery.admin.modulos.whatsapp.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.mydelivery.admin.modulos.whatsapp.dto.WhatsappInstanceDetalheDTO;
import com.mydelivery.admin.modulos.whatsapp.dto.WhatsappInstanceListDTO;
import com.mydelivery.admin.modulos.whatsapp.dto.WhatsappResumoDTO;
import com.mydelivery.admin.shared.exception.NotFoundException;
import com.mydelivery.admin.shared.main.entity.WhatsappInstanceMain;
import com.mydelivery.admin.shared.main.repository.RestauranteMainRepository;
import com.mydelivery.admin.shared.main.repository.WhatsappInstanceMainRepository;

import lombok.RequiredArgsConstructor;

/**
 * Monitoramento das instâncias WhatsApp dos restaurantes (Evolution API).
 *
 * Lê {@code whatsapp_instances} do main DB. Não escreve — operações de reconexão
 * ficariam pra fase futura (precisariam chamar a Evolution API direto, o que é
 * responsabilidade do main app).
 */
@Service
public class WhatsappAdminService {

    private final WhatsappInstanceMainRepository repo;
    private final RestauranteMainRepository restauranteRepo;
    private final EvolutionAdminClient evolution;
    private final RestClient mainClient;
    private final String adminSecret;

    public WhatsappAdminService(
            WhatsappInstanceMainRepository repo,
            RestauranteMainRepository restauranteRepo,
            EvolutionAdminClient evolution,
            @Value("${mydelivery.main-api.base-url:${MAIN_API_BASE_URL:https://api.mydeliveryfood.com.br}}") String mainBaseUrl,
            @Value("${mydelivery.admin.internal-secret:${ADMIN_INTERNAL_SECRET:}}") String adminSecret) {
        this.repo = repo;
        this.restauranteRepo = restauranteRepo;
        this.evolution = evolution;
        this.adminSecret = adminSecret;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) java.time.Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) java.time.Duration.ofSeconds(15).toMillis());
        this.mainClient = RestClient.builder()
                .baseUrl(mainBaseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public WhatsappResumoDTO resumo() {
        long desconectadasTotal = repo.countByStatus(WhatsappInstanceMain.Status.DESCONECTADA);
        long desconectadasInesperadas = repo.countDesconectadasInesperadas();
        long desconectadasManual = repo.countDesconectadasManuais();
        long erros = repo.countByStatus(WhatsappInstanceMain.Status.ERRO);
        return WhatsappResumoDTO.builder()
                .total(repo.count())
                .conectadas(repo.countByStatus(WhatsappInstanceMain.Status.CONECTADA))
                .aguardandoQr(repo.countByStatus(WhatsappInstanceMain.Status.AGUARDANDO_QR))
                .desconectadas(desconectadasTotal)
                .desconectadasInesperadas(desconectadasInesperadas)
                .desconectadasManual(desconectadasManual)
                .erros(erros)
                .novas(repo.countByStatus(WhatsappInstanceMain.Status.NOVA))
                .botInativo(0L)
                // PROBLEMA REAL = queda inesperada + erros (NÃO conta manual)
                .comProblema(desconectadasInesperadas + erros)
                .build();
    }

    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public Page<WhatsappInstanceListDTO> listar(String statusStr, int page, int size) {
        WhatsappInstanceMain.Status status = parseEnum(WhatsappInstanceMain.Status.class, statusStr);
        if (size <= 0 || size > 100) size = 25;
        if (page < 0) page = 0;
        Pageable pageable = PageRequest.of(page, size);

        Page<WhatsappInstanceMain> instancias = repo.buscar(status, pageable);
        Map<Long, String> nomes = nomesRestaurantes(instancias.map(WhatsappInstanceMain::getRestauranteId).toList());
        return instancias.map(w -> WhatsappInstanceListDTO.from(w, nomes.get(w.getRestauranteId())));
    }

    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public WhatsappInstanceDetalheDTO detalhe(Long id) {
        WhatsappInstanceMain w = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Instância WhatsApp não encontrada"));
        return WhatsappInstanceDetalheDTO.from(w, nomeRestaurante(w.getRestauranteId()));
    }

    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public WhatsappInstanceDetalheDTO doRestaurante(Long restauranteId) {
        WhatsappInstanceMain w = repo.findByRestauranteId(restauranteId)
                .orElseThrow(() -> new NotFoundException("Restaurante não tem instância WhatsApp"));
        return WhatsappInstanceDetalheDTO.from(w, nomeRestaurante(restauranteId));
    }

    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public List<WhatsappInstanceListDTO> problematicas(int limite) {
        if (limite < 1 || limite > 200) limite = 50;
        Pageable p = PageRequest.of(0, limite);
        List<WhatsappInstanceMain> rows = repo.findProblematicas(p);
        if (rows.isEmpty()) return List.of();
        Map<Long, String> nomes = nomesRestaurantes(rows.stream().map(WhatsappInstanceMain::getRestauranteId).toList());
        return rows.stream()
                .map(w -> WhatsappInstanceListDTO.from(w, nomes.get(w.getRestauranteId())))
                .toList();
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────

    private String nomeRestaurante(Long id) {
        if (id == null) return null;
        return restauranteRepo.findById(id).map(r -> r.getNome()).orElse(null);
    }

    private Map<Long, String> nomesRestaurantes(List<Long> ids) {
        List<Long> dist = ids.stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (dist.isEmpty()) return new HashMap<>();
        Map<Long, String> out = new HashMap<>();
        restauranteRepo.findAllById(dist).forEach(r -> out.put(r.getId(), r.getNome()));
        return out;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String s) {
        if (s == null || s.isBlank()) return null;
        try { return Enum.valueOf(type, s.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }

    // ─── AÇÕES DE MANUTENÇÃO ──────────────────────────────────────────────

    /**
     * Reinicia a sessão WhatsApp do restaurante chamando Evolution diretamente.
     * SEM logout — mantém pareamento. Usado pelo admin pra "destravar" bots que
     * parecem ter dormido (shadow-ban silencioso do WhatsApp).
     */
    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public Map<String, Object> restart(Long instanceId) {
        WhatsappInstanceMain inst = repo.findById(instanceId)
                .orElseThrow(() -> new NotFoundException("Instância " + instanceId + " não encontrada"));
        if (inst.getInstanceName() == null || inst.getInstanceName().isBlank()) {
            throw new RuntimeException("Instância sem nome configurado");
        }
        // Chama o endpoint novo do main-api — ele registra tentativa + chama
        // Evolution. Assim a contagem de tentativas/snapshot fica consistente.
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = mainClient.post()
                    .uri("/api/admin-internal/whatsapp/{name}/reconectar", inst.getInstanceName())
                    .header("X-Admin-Secret", adminSecret)
                    .retrieve()
                    .body(Map.class);
            Map<String, Object> out = new HashMap<>();
            out.put("ok", resp != null && Boolean.TRUE.equals(resp.get("ok")));
            out.put("instanceName", inst.getInstanceName());
            if (resp != null) out.putAll(resp);
            return out;
        } catch (RestClientResponseException e) {
            // Fallback: chama Evolution direto (comportamento antigo)
            evolution.restart(inst.getInstanceName());
            return Map.of("ok", true, "instanceName", inst.getInstanceName(),
                    "aviso", "main-api indisponível, usado fallback direto");
        }
    }

    /** Saúde REAL — proxy pro main-api que tem o heartbeat de mensagens. */
    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public Map<String, Object> saude(Long instanceId) {
        WhatsappInstanceMain inst = repo.findById(instanceId)
                .orElseThrow(() -> new NotFoundException("Instância " + instanceId + " não encontrada"));
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = mainClient.get()
                    .uri("/api/admin-internal/whatsapp/{name}/saude", inst.getInstanceName())
                    .header("X-Admin-Secret", adminSecret)
                    .retrieve()
                    .body(Map.class);
            return resp != null ? resp : Map.of("erro", "resposta vazia");
        } catch (RestClientResponseException e) {
            return Map.of("erro", "main-api retornou " + e.getStatusCode(),
                    "detalhe", e.getResponseBodyAsString());
        } catch (Exception e) {
            return Map.of("erro", e.getMessage());
        }
    }

    /**
     * RESET COMPLETO da instância — destrava número shadow-banned. Diferente do
     * restart (refresca só o socket): apaga sessão na Evolution e exige novo QR.
     */
    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public Map<String, Object> resetFull(Long instanceId) {
        WhatsappInstanceMain inst = repo.findById(instanceId)
                .orElseThrow(() -> new NotFoundException("Instância " + instanceId + " não encontrada"));
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = mainClient.post()
                    .uri("/api/admin-internal/whatsapp/{name}/reset-full", inst.getInstanceName())
                    .header("X-Admin-Secret", adminSecret)
                    .retrieve()
                    .body(Map.class);
            return resp != null ? resp : Map.of("ok", false, "erro", "resposta vazia");
        } catch (RestClientResponseException e) {
            return Map.of("ok", false, "erro", "main-api " + e.getStatusCode(),
                    "detalhe", e.getResponseBodyAsString());
        } catch (Exception e) {
            return Map.of("ok", false, "erro", e.getMessage());
        }
    }

    /** Agregado horário das últimas N horas pro gráfico global do dashboard. */
    public List<Map<String, Object>> saudeGlobal(int horas) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> resp = mainClient.get()
                    .uri(uri -> uri.path("/api/admin-internal/whatsapp/saude-global")
                            .queryParam("horas", horas).build())
                    .header("X-Admin-Secret", adminSecret)
                    .retrieve()
                    .body(List.class);
            return resp != null ? resp : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Lista incidentes (abertos ou todos recentes). Direto no main, sem id local. */
    public List<Map<String, Object>> listarIncidentes(boolean apenasAbertos) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> resp = mainClient.get()
                    .uri(uri -> uri.path("/api/admin-internal/whatsapp/incidentes")
                            .queryParam("aberto", apenasAbertos).build())
                    .header("X-Admin-Secret", adminSecret)
                    .retrieve()
                    .body(List.class);
            return resp != null ? resp : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Alertas ativos (abertos + não-acked). */
    public List<Map<String, Object>> listarAlertasAtivos() {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> resp = mainClient.get()
                    .uri("/api/admin-internal/whatsapp/incidentes/alertas-ativos")
                    .header("X-Admin-Secret", adminSecret)
                    .retrieve()
                    .body(List.class);
            return resp != null ? resp : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Ações automáticas (filtro opcional por incidente). */
    public List<Map<String, Object>> listarAcoes(Long incidenteId) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> resp = mainClient.get()
                    .uri(uri -> {
                        var b = uri.path("/api/admin-internal/whatsapp/acoes");
                        if (incidenteId != null) b.queryParam("incidente", incidenteId);
                        return b.build();
                    })
                    .header("X-Admin-Secret", adminSecret)
                    .retrieve()
                    .body(List.class);
            return resp != null ? resp : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Marca incidente como visto (silencia alerta sem fechar). */
    public Map<String, Object> ackIncidente(Long incidenteId, String operador) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = mainClient.post()
                    .uri(uri -> uri.path("/api/admin-internal/whatsapp/incidentes/{id}/ack")
                            .queryParam("operador", operador == null ? "admin" : operador)
                            .build(incidenteId))
                    .header("X-Admin-Secret", adminSecret)
                    .retrieve()
                    .body(Map.class);
            return resp != null ? resp : Map.of("ok", false);
        } catch (Exception e) {
            return Map.of("ok", false, "erro", e.getMessage());
        }
    }

    /** Últimos N webhooks recebidos da Evolution pra essa instância. */
    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public Map<String, Object> eventos(Long instanceId) {
        WhatsappInstanceMain inst = repo.findById(instanceId)
                .orElseThrow(() -> new NotFoundException("Instância " + instanceId + " não encontrada"));
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = mainClient.get()
                    .uri("/api/admin-internal/whatsapp/{name}/eventos", inst.getInstanceName())
                    .header("X-Admin-Secret", adminSecret)
                    .retrieve()
                    .body(Map.class);
            return resp != null ? resp : Map.of("erro", "resposta vazia");
        } catch (Exception e) {
            return Map.of("erro", e.getMessage());
        }
    }

    /** Histórico 24h pra gráfico. */
    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public List<Map<String, Object>> historicoSaude(Long instanceId) {
        WhatsappInstanceMain inst = repo.findById(instanceId)
                .orElseThrow(() -> new NotFoundException("Instância " + instanceId + " não encontrada"));
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> resp = mainClient.get()
                    .uri("/api/admin-internal/whatsapp/{name}/historico", inst.getInstanceName())
                    .header("X-Admin-Secret", adminSecret)
                    .retrieve()
                    .body(List.class);
            return resp != null ? resp : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Verifica o estado REAL na Evolution agora (não cache do DB). Útil pra detectar
     * "bot dormindo" — Evolution mostra open mas banco mostra outra coisa, ou
     * Evolution não responde de jeito nenhum.
     *
     * Retorna:
     *   { stateReal: "open"|"close"|"connecting"|"timeout", stateLocal: "...",
     *     coerente: bool, instanceName: "..." }
     */
    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public Map<String, Object> healthCheck(Long instanceId) {
        WhatsappInstanceMain inst = repo.findById(instanceId)
                .orElseThrow(() -> new NotFoundException("Instância " + instanceId + " não encontrada"));
        Map<String, Object> evo = evolution.connectionState(inst.getInstanceName());

        String stateReal = "timeout";
        if (evo != null) {
            Object instData = evo.get("instance");
            if (instData instanceof Map<?, ?> m) {
                Object s = ((Map<String, Object>) m).get("state");
                if (s != null) stateReal = s.toString();
            } else if (evo.get("state") != null) {
                stateReal = evo.get("state").toString();
            } else if (evo.get("erro") != null) {
                stateReal = "timeout";
            }
        }
        String stateLocal = inst.getStatus() == null ? "?" : inst.getStatus().name();
        boolean coerente = ("open".equalsIgnoreCase(stateReal) && "CONECTADA".equals(stateLocal))
                || ("close".equalsIgnoreCase(stateReal) && "DESCONECTADA".equals(stateLocal));
        return Map.of(
                "instanceName", inst.getInstanceName(),
                "stateReal", stateReal,
                "stateLocal", stateLocal,
                "coerente", coerente,
                "raw", evo
        );
    }
}
