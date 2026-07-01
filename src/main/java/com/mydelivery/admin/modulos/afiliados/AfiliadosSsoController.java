package com.mydelivery.admin.modulos.afiliados;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import lombok.extern.slf4j.Slf4j;

/**
 * SSO pro painel de afiliados — pega um JWT admin do myafiliados-api usando
 * X-Admin-Secret compartilhado e devolve a URL pronta pro navegador redirecionar.
 *
 * Fluxo: admin clica "Afiliados" no sidebar → JS chama POST /api/admin/afiliados/sso
 * → recebe { url } → window.location = url → painel afiliados detecta ?sso=<jwt>
 * e loga automaticamente.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/afiliados")
@PreAuthorize("hasRole('ADMIN')")
public class AfiliadosSsoController {

    private final RestClient afiliadosClient;
    private final String adminSecret;
    private final String painelUrl;

    public AfiliadosSsoController(
            @Value("${mydelivery.afiliados.api-base-url:${AFILIADOS_API_BASE_URL:}}") String afiliadosBaseUrl,
            @Value("${mydelivery.afiliados.admin-secret:${AFILIADOS_ADMIN_SECRET:}}") String adminSecret,
            @Value("${mydelivery.afiliados.painel-url:${AFILIADOS_PAINEL_URL:https://afiliados.mydeliveryfood.com.br}}") String painelUrl) {
        this.adminSecret = adminSecret;
        this.painelUrl = painelUrl == null || painelUrl.isBlank()
                ? "https://afiliados.mydeliveryfood.com.br"
                : painelUrl.replaceAll("/+$", "");
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(15).toMillis());
        String base = afiliadosBaseUrl == null ? "" : afiliadosBaseUrl.trim();
        // Se ainda não configurado, deixa builder sem baseUrl — não quebra o startup.
        // A chamada falhará em runtime com msg clara.
        this.afiliadosClient = base.isEmpty()
                ? RestClient.builder().requestFactory(factory).build()
                : RestClient.builder()
                    .baseUrl(base)
                    .requestFactory(factory)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
    }

    @PostMapping("/sso")
    public ResponseEntity<?> sso() {
        if (adminSecret == null || adminSecret.isBlank()) {
            return ResponseEntity.status(500).body(Map.of(
                    "erro", "AFILIADOS_ADMIN_SECRET não configurado no Railway"));
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = afiliadosClient.post()
                    .uri("/api/admin-internal/sso-token")
                    .header("X-Admin-Secret", adminSecret)
                    .retrieve()
                    .body(Map.class);
            if (resp == null || resp.get("token") == null) {
                return ResponseEntity.status(502).body(Map.of("erro", "myafiliados-api não devolveu token"));
            }
            String token = String.valueOf(resp.get("token"));
            String url = painelUrl + "/admin.html?sso=" + token + "&role=ADMIN_AFILIADOS";
            log.info("[Afiliados-SSO] token gerado, redirecionando");
            return ResponseEntity.ok(Map.of("url", url));
        } catch (RestClientResponseException e) {
            log.warn("[Afiliados-SSO] myafiliados-api rejeitou: {}", e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("erro", "SSO rejeitado: " + e.getStatusCode()));
        } catch (Exception e) {
            log.error("[Afiliados-SSO] erro: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("erro", e.getMessage()));
        }
    }
}
