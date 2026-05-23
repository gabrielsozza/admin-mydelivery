package com.mydelivery.admin.modulos.pagamentos.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.mydelivery.admin.modulos.configuracoes.service.ConfiguracaoAdminService;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Valida assinatura HMAC-SHA256 enviada pelo MP no header {@code x-signature}.
 *
 * Formato MP:
 *   header x-signature:  "ts=1704908010,v1=618c85345248dd820d5c4c8c8c8c8c8c..."
 *   header x-request-id: "<uuid>"
 *   payload assinado:    "id:&lt;dataId&gt;;request-id:&lt;xRequestId&gt;;ts:&lt;ts&gt;;"
 *
 * Modo "estrito" liga automaticamente quando {@code admin.mp.webhook-secret}
 * está setado. Em dev (sem secret), validação fica desligada e tudo passa
 * (com warn no boot).
 *
 * Ref MP: https://www.mercadopago.com.br/developers/pt/docs/your-integrations/notifications/webhooks
 */
@Slf4j
@Component
public class MpSignatureValidator {

    /** Janela máxima de tolerância pro timestamp (replay attack). */
    private static final long MAX_AGE_SECONDS = 300; // 5 min

    private final String envSecret;
    private final ConfiguracaoAdminService configService;

    public MpSignatureValidator(@Value("${admin.mp.webhook-secret:}") String envSecret,
                                ConfiguracaoAdminService configService) {
        this.envSecret = envSecret == null ? "" : envSecret.trim();
        this.configService = configService;
    }

    @PostConstruct
    void avisarModo() {
        log.info("[MpSignature] inicializado — modo (estrito/permissivo) é decidido por chamada, "
                + "lendo do DB com fallback pro .env. Configure em /api/admin/configuracoes/mp.webhook_secret.");
    }

    /** Secret efetivo (DB tem precedência, fallback pro .env). */
    private String resolverSecret() {
        return configService.obter(ConfiguracaoAdminService.CHAVE_MP_WEBHOOK_SECRET, envSecret);
    }

    public boolean strict() {
        String s = resolverSecret();
        return s != null && !s.isBlank();
    }

    /**
     * @return true se a request é válida ou se estamos em modo permissivo
     */
    public boolean validar(HttpServletRequest req, Long paymentId) {
        String secret = resolverSecret();
        boolean strict = secret != null && !secret.isBlank();
        if (!strict) return true;

        String sig = req.getHeader("x-signature");
        String requestId = req.getHeader("x-request-id");
        if (sig == null || sig.isBlank() || requestId == null || requestId.isBlank() || paymentId == null) {
            log.warn("[MpSignature] rejeitado — headers ausentes (sig={}, reqId={}, paymentId={})",
                    sig != null, requestId != null, paymentId);
            return false;
        }

        Map<String, String> parts = new HashMap<>();
        for (String p : sig.split(",")) {
            String[] kv = p.trim().split("=", 2);
            if (kv.length == 2) parts.put(kv[0].trim(), kv[1].trim());
        }
        String ts = parts.get("ts");
        String v1 = parts.get("v1");
        if (ts == null || v1 == null) {
            log.warn("[MpSignature] rejeitado — x-signature sem ts ou v1: {}", sig);
            return false;
        }

        // 1) Timestamp não pode ser muito antigo (replay)
        try {
            long tsLong = Long.parseLong(ts);
            long ageSec = Math.abs(Instant.now().getEpochSecond() - tsLong);
            if (ageSec > MAX_AGE_SECONDS) {
                log.warn("[MpSignature] rejeitado — ts muito antigo ({} s)", ageSec);
                return false;
            }
        } catch (NumberFormatException e) {
            log.warn("[MpSignature] rejeitado — ts inválido: {}", ts);
            return false;
        }

        // 2) Reconstrói payload e compara HMAC
        String payload = "id:" + paymentId + ";request-id:" + requestId + ";ts:" + ts + ";";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(hmac);
            // Constant-time compare pra evitar timing attack
            boolean ok = MessageDigest.isEqual(
                    hex.getBytes(StandardCharsets.UTF_8),
                    v1.getBytes(StandardCharsets.UTF_8));
            if (!ok) {
                log.warn("[MpSignature] rejeitado — HMAC mismatch pra paymentId={}", paymentId);
            }
            return ok;
        } catch (Exception e) {
            log.error("[MpSignature] erro validando: {}", e.getMessage(), e);
            return false;
        }
    }
}
