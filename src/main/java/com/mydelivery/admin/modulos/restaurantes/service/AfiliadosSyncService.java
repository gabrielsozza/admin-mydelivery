package com.mydelivery.admin.modulos.restaurantes.service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.mydelivery.admin.shared.main.entity.RestauranteMain;

import lombok.extern.slf4j.Slf4j;

/**
 * Notifica o myafiliados-api quando o admin faz vínculo/desvínculo manual
 * entre restaurante e afiliado.
 *
 * Sem esse ponto, o painel do afiliado nunca ficaria sabendo do vínculo
 * criado pelo admin — os contadores (indicados, em trial, ativos, cancelados)
 * seguiriam desatualizados. Ver {@code MydeliveryWebhookService.tratarVinculoManual}
 * do lado myafiliados-api pra ver o handler que cria/atualiza o AfiliadoVinculo.
 *
 * Fail-safe: NUNCA propaga erro pro fluxo do admin. Se o myafiliados estiver
 * fora, só loga warning. Async pra não segurar a resposta do PATCH.
 */
@Slf4j
@Service
public class AfiliadosSyncService {

    private final RestClient client;
    private final String secret;
    private final String baseUrl;
    private final boolean ativo;

    public AfiliadosSyncService(
            @Value("${mydelivery.afiliados.api-base-url:${AFILIADOS_API_BASE_URL:}}") String baseUrlEnv,
            // Mesmo valor usado como AFILIADOS_ADMIN_SECRET. O webhook do myafiliados-api
            // espera esse secret no header X-Webhook-Secret. Manter os dois env vars
            // (webhook + admin) apontando pro mesmo valor evita zoada de config.
            @Value("${mydelivery.afiliados.admin-secret:${AFILIADOS_ADMIN_SECRET:${AFILIADOS_WEBHOOK_SECRET:}}}") String secret) {
        this.secret = secret == null ? "" : secret.trim();
        String b = baseUrlEnv == null ? "" : baseUrlEnv.trim();
        if (!b.isEmpty() && !b.startsWith("http://") && !b.startsWith("https://")) {
            b = "https://" + b;
        }
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        this.baseUrl = b;
        this.ativo = !this.baseUrl.isEmpty() && !this.secret.isEmpty();

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(8).toMillis());
        this.client = RestClient.builder().requestFactory(factory).build();

        log.info("[AfiliadosSync] boot: ativo={} baseUrl={} secret={}chars",
                ativo, ativo ? baseUrl : "(inativo)", this.secret.length());
    }

    /** Envia VINCULO_MANUAL com status atual do restaurante. */
    @Async
    public void vinculoManual(RestauranteMain r, String codigoAfiliado, String emailDono) {
        if (!ativo || r == null || codigoAfiliado == null || codigoAfiliado.isBlank()) return;
        Map<String, Object> body = base("VINCULO_MANUAL", r, emailDono);
        body.put("codigoAfiliado", codigoAfiliado);
        body.put("statusRestaurante", r.getStatus() == null ? "TRIAL" : r.getStatus().name());
        enviar(body);
    }

    /** Envia DESVINCULO_MANUAL. codigoAfiliado é o que estava vinculado ANTES. */
    @Async
    public void desvinculoManual(RestauranteMain r, String codigoAfiliadoAnterior, String emailDono) {
        if (!ativo || r == null || codigoAfiliadoAnterior == null || codigoAfiliadoAnterior.isBlank()) return;
        Map<String, Object> body = base("DESVINCULO_MANUAL", r, emailDono);
        body.put("codigoAfiliado", codigoAfiliadoAnterior);
        enviar(body);
    }

    private Map<String, Object> base(String tipo, RestauranteMain r, String emailDono) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipo", tipo);
        m.put("restauranteId", r.getId());
        m.put("restauranteNome", r.getNome());
        m.put("restauranteSlug", r.getSlug());
        if (emailDono != null) m.put("restauranteEmail", emailDono);
        return m;
    }

    private void enviar(Map<String, Object> body) {
        try {
            client.post()
                    .uri(baseUrl + "/api/webhooks/mydelivery/evento")
                    .header("X-Webhook-Secret", secret)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[AfiliadosSync] {} enviado pra restaurante={} afiliado={}",
                    body.get("tipo"), body.get("restauranteId"), body.get("codigoAfiliado"));
        } catch (Exception e) {
            log.warn("[AfiliadosSync] {} FALHOU pra restaurante={}: {}",
                    body.get("tipo"), body.get("restauranteId"), e.getMessage());
        }
    }
}
