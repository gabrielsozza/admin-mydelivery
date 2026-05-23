package com.mydelivery.admin.modulos.pagamentos;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mydelivery.admin.modulos.pagamentos.service.MpSignatureValidator;
import com.mydelivery.admin.modulos.pagamentos.service.PagamentoConfirmService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Webhook do Mercado Pago (PÚBLICO — sem auth JWT).
 *
 * MP manda 2 formatos dependendo da feature:
 *  - POST body JSON:   {"type":"payment","data":{"id":12345}}
 *  - Query string:     ?topic=payment&id=12345  (legacy IPN)
 *
 * Defesa em camadas:
 *  1. Validação de signature HMAC-SHA256 (header x-signature) — quando o secret
 *     está configurado. Em modo permissivo (dev), aceita sem checar.
 *  2. Refetch autenticado via MP API — nunca confia no body do webhook, sempre
 *     consulta MP novamente pra obter o status real do pagamento.
 *
 * Logs intencionalmente NÃO incluem o body completo — pode conter PII do pagador.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/mp-webhook")
@RequiredArgsConstructor
public class MpWebhookController {

    private final PagamentoConfirmService confirmService;
    private final MpSignatureValidator signatureValidator;

    @PostMapping
    public ResponseEntity<Map<String, Object>> receberPost(
            HttpServletRequest req,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String id) {

        Long paymentId = extrairPaymentId(body, topic, id);
        log.info("[MpWebhook] POST recebido — paymentId={}, topic={}, requestId={}",
                paymentId, topic, req.getHeader("x-request-id"));

        if (paymentId == null) {
            return ResponseEntity.ok(Map.of("ok", true, "ignored", true));
        }

        if (!signatureValidator.validar(req, paymentId)) {
            log.warn("[MpWebhook] assinatura inválida ou ausente — paymentId={}", paymentId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "ok", false, "erro", "signature inválida ou ausente"));
        }

        boolean alterou = confirmService.processarNotificacao(paymentId);
        return ResponseEntity.ok(Map.of("ok", true, "alterou", alterou, "paymentId", paymentId));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> receberGet(
            HttpServletRequest req,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String id) {

        Long paymentId = extrairPaymentId(null, topic, id);
        log.info("[MpWebhook] GET recebido — topic={} paymentId={} requestId={}",
                topic, paymentId, req.getHeader("x-request-id"));

        if (paymentId == null) {
            return ResponseEntity.ok(Map.of("ok", true, "ignored", true));
        }

        if (!signatureValidator.validar(req, paymentId)) {
            log.warn("[MpWebhook] assinatura inválida ou ausente — paymentId={}", paymentId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "ok", false, "erro", "signature inválida ou ausente"));
        }

        boolean alterou = confirmService.processarNotificacao(paymentId);
        return ResponseEntity.ok(Map.of("ok", true, "alterou", alterou, "paymentId", paymentId));
    }

    /**
     * Extrai o paymentId de qualquer um dos formatos:
     *  - body.data.id      (JSON v2)
     *  - body.id           (alguns formatos)
     *  - query ?id=        (IPN legacy)
     */
    static Long extrairPaymentId(Map<String, Object> body, String topic, String queryId) {
        // só consideramos eventos de payment
        if (topic != null && !topic.isBlank()
                && !"payment".equalsIgnoreCase(topic.trim())
                && body == null) {
            return null;
        }
        if (body != null) {
            String type = String.valueOf(body.getOrDefault("type", body.getOrDefault("topic", "")));
            if (type != null && !type.isBlank() && !"payment".equalsIgnoreCase(type.trim())) {
                return null;
            }
            Object data = body.get("data");
            if (data instanceof Map<?, ?> dataMap) {
                Object idObj = dataMap.get("id");
                Long parsed = tentarLong(idObj);
                if (parsed != null) return parsed;
            }
            Long idTop = tentarLong(body.get("id"));
            if (idTop != null) return idTop;
        }
        return tentarLong(queryId);
    }

    private static Long tentarLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
