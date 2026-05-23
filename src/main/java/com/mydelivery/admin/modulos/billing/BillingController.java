package com.mydelivery.admin.modulos.billing;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.mydelivery.admin.shared.main.entity.PagamentoMensalidadeMain;
import com.mydelivery.admin.shared.main.entity.RestauranteMain;
import com.mydelivery.admin.shared.main.repository.PagamentoMensalidadeMainRepository;
import com.mydelivery.admin.shared.main.repository.RestauranteMainRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Admin billing — listar falhas de pagamento + conceder meses grátis.
 *
 * - GET /api/admin/billing/pagamentos: lista falhas/sucessos com filtros
 * - POST /api/admin/billing/conceder-meses-gratis: chama main API c/ secret
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/billing")
@PreAuthorize("hasAnyRole('ADMIN', 'FINANCEIRO', 'SUPORTE')")
public class BillingController {

    private final PagamentoMensalidadeMainRepository pagRepo;
    private final RestauranteMainRepository restRepo;
    private final RestClient mainClient;
    private final String adminSecret;

    public BillingController(
            PagamentoMensalidadeMainRepository pagRepo,
            RestauranteMainRepository restRepo,
            @Value("${mydelivery.main-api.base-url:${MAIN_API_BASE_URL:https://api.mydeliveryfood.com.br}}") String mainBaseUrl,
            @Value("${mydelivery.admin.internal-secret:${ADMIN_INTERNAL_SECRET:}}") String adminSecret) {
        this.pagRepo = pagRepo;
        this.restRepo = restRepo;
        this.adminSecret = adminSecret;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(15).toMillis());
        this.mainClient = RestClient.builder()
                .baseUrl(mainBaseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /** Lista pagamentos de mensalidade com filtros — útil pra debug de falhas. */
    @GetMapping("/pagamentos")
    public ResponseEntity<Map<String, Object>> listar(
            @RequestParam(required = false) String status,       // PAGO|REJEITADO|PENDENTE
            @RequestParam(required = false) Long restauranteId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        PagamentoMensalidadeMain.Status st = parseEnum(status);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        Page<PagamentoMensalidadeMain> p = pagRepo.buscar(st, restauranteId, pageable);

        // Hidrata nome do restaurante (batch)
        Map<Long, String> nomes = new java.util.HashMap<>();
        if (!p.isEmpty()) {
            List<Long> ids = p.getContent().stream().map(PagamentoMensalidadeMain::getRestauranteId)
                    .filter(java.util.Objects::nonNull).distinct().toList();
            restRepo.findAllById(ids).forEach(r -> nomes.put(r.getId(), r.getNome()));
        }

        List<Map<String, Object>> linhas = p.getContent().stream().map(pag -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", pag.getId());
            m.put("restauranteId", pag.getRestauranteId());
            m.put("restauranteNome", nomes.get(pag.getRestauranteId()));
            m.put("plano", pag.getPlano());
            m.put("valor", pag.getValor());
            m.put("status", pag.getStatus() == null ? null : pag.getStatus().name());
            m.put("metodoPagamento", pag.getMetodoPagamento());
            m.put("categoriaErro", pag.getCategoriaErro());
            m.put("motivoErro", pag.getMotivoErro());
            m.put("mpPaymentId", pag.getMpPaymentId());
            m.put("mpStatusDetail", pag.getMpStatusDetail());
            m.put("criadoEm", pag.getCriadoEm() == null ? null : pag.getCriadoEm().toString());
            m.put("pagoEm", pag.getPagoEm() == null ? null : pag.getPagoEm().toString());
            return m;
        }).toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", linhas);
        out.put("totalElements", p.getTotalElements());
        out.put("totalPages", p.getTotalPages());
        out.put("number", p.getNumber());
        out.put("size", p.getSize());
        return ResponseEntity.ok(out);
    }

    /** Resumo: contagem por status pra dashboard. */
    @GetMapping("/pagamentos/resumo")
    public ResponseEntity<Map<String, Object>> resumo() {
        return ResponseEntity.ok(Map.of(
                "pagos", pagRepo.countByStatus(PagamentoMensalidadeMain.Status.PAGO),
                "rejeitados", pagRepo.countByStatus(PagamentoMensalidadeMain.Status.REJEITADO),
                "pendentes", pagRepo.countByStatus(PagamentoMensalidadeMain.Status.PENDENTE)
        ));
    }

    /**
     * Concede N meses grátis pra um restaurante via API do main.
     * Body: { restauranteId, meses, motivo? }
     */
    @PostMapping("/conceder-meses-gratis")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCEIRO')")
    public ResponseEntity<?> concederMesesGratis(@RequestBody Map<String, Object> body) {
        if (adminSecret == null || adminSecret.isBlank()) {
            return ResponseEntity.status(500)
                    .body(Map.of("erro", "ADMIN_INTERNAL_SECRET não configurado no Railway"));
        }
        try {
            Map<String, Object> resp = mainClient.post()
                    .uri("/api/restaurante/assinatura/conceder-meses-gratis-admin")
                    .header("X-Admin-Secret", adminSecret)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return ResponseEntity.ok(resp);
        } catch (RestClientResponseException e) {
            log.warn("[Billing] main API rejeitou: {}", e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("erro", e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.error("[Billing] erro ao chamar main API: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("erro", e.getMessage()));
        }
    }

    private PagamentoMensalidadeMain.Status parseEnum(String s) {
        if (s == null || s.isBlank()) return null;
        try { return PagamentoMensalidadeMain.Status.valueOf(s.trim().toUpperCase()); }
        catch (Exception e) { return null; }
    }
}
