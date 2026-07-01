package com.mydelivery.admin.modulos.restaurantes;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.mydelivery.admin.modulos.restaurantes.dto.RestauranteDetalheDTO;
import com.mydelivery.admin.modulos.restaurantes.dto.RestauranteListDTO;
import com.mydelivery.admin.modulos.restaurantes.service.RestaurantesService;

import lombok.extern.slf4j.Slf4j;

/**
 * Endpoints de leitura/gestão dos restaurantes (admin).
 *
 *  GET /api/admin/restaurantes?status=ATIVO&q=pizza&page=0&size=25
 *  GET /api/admin/restaurantes/{id}
 *
 * Tudo aqui exige role ADMIN (granular pro futuro: SUPORTE pode ver lista mas
 * não pode mexer; FINANCEIRO foca em faturamento, etc — depois).
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/restaurantes")
@PreAuthorize("hasRole('ADMIN')")
public class RestaurantesController {

    private final RestaurantesService service;
    private final RestClient mainClient;
    private final String adminSecret;

    public RestaurantesController(
            RestaurantesService service,
            @Value("${mydelivery.main-api.base-url:${MAIN_API_BASE_URL:https://api.mydeliveryfood.com.br}}") String mainBaseUrl,
            @Value("${mydelivery.admin.internal-secret:${ADMIN_INTERNAL_SECRET:}}") String adminSecret) {
        this.service = service;
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

    @GetMapping
    public ResponseEntity<Page<RestauranteListDTO>> listar(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(service.listar(status, q, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RestauranteDetalheDTO> detalhe(@PathVariable Long id) {
        return ResponseEntity.ok(service.detalhe(id));
    }

    /**
     * Redefine senha do dono do restaurante. Usado pelo suporte quando
     * o cliente perde acesso e o "esqueci minha senha" não funciona.
     *
     * Body opcional: {@code { "novaSenha": "abc123" }}.
     * Se {@code novaSenha} vier vazia/null, gera senha aleatória.
     * Retorna a senha em texto puro pra admin comunicar ao cliente.
     */
    @PostMapping("/{id}/redefinir-senha")
    public ResponseEntity<Map<String, Object>> redefinirSenha(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String novaSenha = (body != null) ? body.get("novaSenha") : null;
        return ResponseEntity.ok(service.redefinirSenha(id, novaSenha));
    }

    /**
     * Apaga DEFINITIVAMENTE o restaurante e tudo associado.
     * Operação irreversível — usar pra limpar cadastros lixo.
     */
    /**
     * Define valores personalizados de plano pra um restaurante específico.
     * Body: { valorMensal, valorSemestral, valorAnual } — null/vazio mantém default.
     * Usado pra R$ 50 pra antigos, R$ 75 pra novos etc.
     */
    @PostMapping("/{id}/precificar")
    public ResponseEntity<?> precificar(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        if (adminSecret == null || adminSecret.isBlank()) {
            return ResponseEntity.status(500)
                    .body(Map.of("erro", "ADMIN_INTERNAL_SECRET não configurado"));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("restauranteId", id);
        payload.put("valorMensal", body.get("valorMensal"));
        payload.put("valorSemestral", body.get("valorSemestral"));
        payload.put("valorAnual", body.get("valorAnual"));
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = mainClient.post()
                    .uri("/api/restaurante/assinatura/precificar-restaurante-admin")
                    .header("X-Admin-Secret", adminSecret)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            log.info("[Precificar] restaurante={} → {}", id, resp);
            return ResponseEntity.ok(resp);
        } catch (RestClientResponseException e) {
            log.warn("[Precificar] main API rejeitou: {}", e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("erro", e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.error("[Precificar] erro: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("erro", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> apagar(@PathVariable Long id) {
        Map<String, Integer> detalhe = service.apagarDefinitivamente(id);
        int total = detalhe.values().stream().mapToInt(Integer::intValue).sum();
        return ResponseEntity.ok(Map.of(
            "ok", true,
            "restauranteId", id,
            "linhasRemovidas", total,
            "detalhe", detalhe,
            "mensagem", "Restaurante apagado definitivamente (" + total + " registros removidos)."
        ));
    }
}
