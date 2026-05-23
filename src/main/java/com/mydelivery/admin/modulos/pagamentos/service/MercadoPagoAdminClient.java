package com.mydelivery.admin.modulos.pagamentos.service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.mydelivery.admin.modulos.configuracoes.service.ConfiguracaoAdminService;
import com.mydelivery.admin.modulos.pagamentos.dto.MpPaymentResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Cliente HTTP enxuto pro MP Cloud API.
 *
 * Usa o {@code admin.mp.access-token} (DIFERENTE do token de cada restaurante).
 * Esse é o token da conta MyDelivery que cobra mensalidade dos restaurantes.
 *
 * Endpoints usados:
 *  - POST {api-base}/v1/payments        → criar pagamento (PIX/boleto)
 *  - GET  {api-base}/v1/payments/{id}   → consultar status
 *
 * Cada POST manda {@code X-Idempotency-Key} pra evitar duplicata de cobrança
 * se a requisição for retentada.
 */
@Slf4j
@Component
public class MercadoPagoAdminClient {

    private final RestClient http;
    private final String envAccessToken;
    private final ConfiguracaoAdminService configService;

    public MercadoPagoAdminClient(
            @Value("${admin.mp.api-base:https://api.mercadopago.com}") String apiBase,
            @Value("${admin.mp.access-token:}") String envAccessToken,
            ConfiguracaoAdminService configService) {
        this.envAccessToken = envAccessToken;
        this.configService = configService;
        this.http = RestClient.builder()
                .baseUrl(apiBase)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Resolve o access token na hora da chamada: DB (configurado via painel)
     * tem precedência. Cai pro .env se o DB não estiver preenchido.
     */
    private String resolverAccessToken() {
        return configService.obter(ConfiguracaoAdminService.CHAVE_MP_ACCESS_TOKEN, envAccessToken);
    }

    public boolean configurado() {
        String t = resolverAccessToken();
        return t != null && !t.isBlank();
    }

    /** Cria cobrança PIX. Retorna a resposta enxuta com qr_code e id. */
    public MpPaymentResponse criarCobrancaPix(BigDecimal valor,
                                              String descricao,
                                              String payerEmail,
                                              String externalReference,
                                              String notificationUrl) {
        garantirConfigurado();

        Map<String, Object> body = Map.of(
                "transaction_amount", valor,
                "payment_method_id", "pix",
                "description", truncate(descricao, 256),
                "payer", Map.of("email", payerEmail),
                "external_reference", externalReference,
                "notification_url", notificationUrl
        );

        return postPagamento(body, externalReference);
    }

    /** Cria cobrança boleto (mais lento, mas útil pra alguns lojistas). */
    public MpPaymentResponse criarCobrancaBoleto(BigDecimal valor,
                                                 String descricao,
                                                 String payerEmail,
                                                 String payerCpfNumeros,
                                                 String payerNome,
                                                 String externalReference,
                                                 String notificationUrl) {
        garantirConfigurado();

        // MP exige first_name/last_name pra boleto. Quebra simples.
        String first = payerNome;
        String last = "";
        if (payerNome != null) {
            String[] partes = payerNome.trim().split("\\s+", 2);
            first = partes[0];
            last = partes.length > 1 ? partes[1] : "MyDelivery";
        }

        Map<String, Object> body = Map.of(
                "transaction_amount", valor,
                "payment_method_id", "bolbradesco",
                "description", truncate(descricao, 256),
                "payer", Map.of(
                        "email", payerEmail,
                        "first_name", first == null ? "Cliente" : first,
                        "last_name", last,
                        "identification", Map.of(
                                "type", "CPF",
                                "number", payerCpfNumeros == null ? "" : payerCpfNumeros
                        )
                ),
                "external_reference", externalReference,
                "notification_url", notificationUrl
        );

        return postPagamento(body, externalReference);
    }

    /** Consulta status de um pagamento existente (usado pelo webhook). */
    public MpPaymentResponse consultarPagamento(Long paymentId) {
        garantirConfigurado();
        try {
            MpPaymentResponse resp = http.get()
                    .uri("/v1/payments/{id}", paymentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolverAccessToken())
                    .retrieve()
                    .body(MpPaymentResponse.class);
            if (resp == null) {
                throw new IllegalStateException("MP retornou body null pro payment " + paymentId);
            }
            return resp;
        } catch (HttpClientErrorException e) {
            log.error("[MpClient] consultarPagamento {} retornou {}: {}",
                    paymentId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException(
                    "MP falhou ao consultar pagamento " + paymentId + " (HTTP " + e.getStatusCode() + ")", e);
        }
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────

    private MpPaymentResponse postPagamento(Map<String, Object> body, String externalReference) {
        // Idempotency-Key estável: só externalReference. Se a request sofrer
        // timeout/retry, o MP retorna o MESMO payment (não cria duplicado).
        // Pra reemitir cobrança (PIX vencido), o caller pode passar uma chave
        // composta com sufixo — mas em v1 todas usam só fatura-{id}.
        String idempotencyKey = externalReference != null && !externalReference.isBlank()
                ? externalReference
                : "noref-" + UUID.randomUUID();
        try {
            MpPaymentResponse resp = http.post()
                    .uri("/v1/payments")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolverAccessToken())
                    .header("X-Idempotency-Key", idempotencyKey)
                    .body(body)
                    .retrieve()
                    .body(MpPaymentResponse.class);
            if (resp == null || resp.getId() == null) {
                throw new IllegalStateException("MP retornou resposta inválida (sem id)");
            }
            log.info("[MpClient] payment criado id={} status={} ref={}",
                    resp.getId(), resp.getStatus(), externalReference);
            return resp;
        } catch (HttpClientErrorException e) {
            log.error("[MpClient] POST /v1/payments retornou {}: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("MP rejeitou a cobrança: " + e.getStatusCode()
                    + " — " + e.getResponseBodyAsString(), e);
        }
    }

    private void garantirConfigurado() {
        if (!configurado()) {
            throw new IllegalStateException(
                    "Mercado Pago não configurado — defina ADMIN_MP_ACCESS_TOKEN no .env / Railway");
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
