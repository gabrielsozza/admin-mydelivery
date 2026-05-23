package com.mydelivery.admin.modulos.whatsapp.service;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Cliente HTTP leve pra Evolution API — usado SÓ pelo admin pra fazer
 * operações de manutenção (restart, health-check) nas instâncias dos
 * restaurantes.
 *
 * Reutiliza as MESMAS env vars do main app: EVOLUTION_BASE_URL e
 * EVOLUTION_API_KEY. Em produção, devem ser configuradas no Railway
 * do admin-mydelivery-api também.
 */
@Slf4j
@Component
public class EvolutionAdminClient {

    private final RestClient client;
    private final String apiKey;

    public EvolutionAdminClient(
            @Value("${mydelivery.evolution.base-url:${EVOLUTION_BASE_URL:}}") String baseUrl,
            @Value("${mydelivery.evolution.api-key:${EVOLUTION_API_KEY:}}") String apiKey,
            @Value("${mydelivery.evolution.timeout-ms:15000}") int timeoutMs) {
        this.apiKey = apiKey;
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("[EvolutionAdmin] EVOLUTION_BASE_URL não configurado — restart vai falhar");
            baseUrl = "http://localhost:8081";
        }
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofMillis(timeoutMs).toMillis());
        factory.setReadTimeout((int) Duration.ofMillis(timeoutMs).toMillis());
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("apikey", apiKey == null ? "" : apiKey)
                .build();
    }

    /** Reinicia sessão WebSocket SEM precisar de QR novo. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> restart(String instanceName) {
        try {
            return client.post().uri("/instance/restart/{name}", instanceName)
                    .body("").retrieve().body(Map.class);
        } catch (Exception e) {
            log.warn("[EvolutionAdmin] restart {} falhou: {}", instanceName, e.getMessage());
            throw new RuntimeException("Não consegui reiniciar — " + e.getMessage());
        }
    }

    /** Consulta status real-time da Evolution: { instance: { state: "open" | "close" | "connecting" } } */
    @SuppressWarnings("unchecked")
    public Map<String, Object> connectionState(String instanceName) {
        try {
            return client.get().uri("/instance/connectionState/{name}", instanceName)
                    .retrieve().body(Map.class);
        } catch (Exception e) {
            log.warn("[EvolutionAdmin] connectionState {} falhou: {}", instanceName, e.getMessage());
            return Map.of("erro", e.getMessage());
        }
    }
}
