package com.mydelivery.admin.modulos.pagamentos.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mydelivery.admin.modulos.faturamento.entity.Fatura;
import com.mydelivery.admin.modulos.faturamento.repository.FaturaRepository;
import com.mydelivery.admin.modulos.pagamentos.dto.MpPaymentResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Recebe notificação do MP (webhook) e atualiza a Fatura.
 *
 * Estratégia:
 *  1. Webhook só traz {id do payment}. NÃO confiamos no payload — refazemos a
 *     consulta autenticada via MercadoPagoAdminClient. Isso protege contra
 *     webhooks falsificados (até implementarmos validação de signature).
 *  2. Pega external_reference do MP → extrai fatura_id.
 *  3. Se status do MP for "approved" → marca PAGA, salva pagamento_em.
 *  4. Outros status → só atualiza observação no histórico.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PagamentoConfirmService {

    private final MercadoPagoAdminClient mp;
    private final FaturaRepository faturaRepo;

    /**
     * Processa um payment id vindo do webhook.
     * @return true se algum estado mudou
     */
    @Transactional
    public boolean processarNotificacao(Long paymentId) {
        if (paymentId == null) return false;
        MpPaymentResponse resp;
        try {
            resp = mp.consultarPagamento(paymentId);
        } catch (Exception e) {
            log.warn("[Webhook] falha ao consultar payment {}: {}", paymentId, e.getMessage());
            return false;
        }

        String extRef = resp.getExternalReference();
        Long faturaId = extrairFaturaId(extRef);
        if (faturaId == null) {
            log.warn("[Webhook] payment {} sem external_reference reconhecível ({}). Ignorando.",
                    paymentId, extRef);
            return false;
        }

        Fatura f = faturaRepo.findById(faturaId).orElse(null);
        if (f == null) {
            log.warn("[Webhook] payment {} aponta pra fatura {} que não existe.", paymentId, faturaId);
            return false;
        }

        // Sempre acompanha o último paymentId visto pra essa fatura
        f.setExternalPaymentId(String.valueOf(paymentId));

        if ("approved".equalsIgnoreCase(resp.getStatus())) {
            if (f.getStatus() == Fatura.Status.PAGA) {
                log.info("[Webhook] fatura {} já estava PAGA — ignorado (idempotente)", faturaId);
                faturaRepo.save(f);
                return false;
            }
            f.setStatus(Fatura.Status.PAGA);
            f.setMetodoPagamento(metodoDe(resp.getPaymentMethodId()));
            f.setPagamentoEm(parsarDataAprovacao(resp.getDateApproved()));
            f.setObservacao(prepend(f.getObservacao(),
                    "[" + LocalDateTime.now() + "] Pagamento confirmado via webhook MP — payment "
                            + paymentId + " status=approved"));
            faturaRepo.save(f);
            log.info("[Webhook] ✓ fatura {} marcada PAGA (payment {})", faturaId, paymentId);
            return true;
        }

        // Status não-approved → registra histórico, não muda status da fatura
        f.setObservacao(prepend(f.getObservacao(),
                "[" + LocalDateTime.now() + "] Notificação MP — payment " + paymentId
                        + " status=" + resp.getStatus()
                        + (resp.getStatusDetail() != null ? "/" + resp.getStatusDetail() : "")));
        faturaRepo.save(f);
        return false;
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────

    /** "fatura-123" → 123. Tolerante: aceita só número também. */
    static Long extrairFaturaId(String ref) {
        if (ref == null || ref.isBlank()) return null;
        String s = ref.trim();
        if (s.startsWith("fatura-")) s = s.substring("fatura-".length());
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Fatura.MetodoPagamento metodoDe(String mpMethodId) {
        if (mpMethodId == null) return Fatura.MetodoPagamento.OUTRO;
        return switch (mpMethodId.toLowerCase()) {
            case "pix" -> Fatura.MetodoPagamento.PIX;
            case "bolbradesco", "boleto" -> Fatura.MetodoPagamento.BOLETO;
            default -> Fatura.MetodoPagamento.CARTAO; // assume cartão pra outros métodos MP
        };
    }

    private static LocalDateTime parsarDataAprovacao(String dataIso) {
        if (dataIso == null || dataIso.isBlank()) return LocalDateTime.now();
        try {
            return OffsetDateTime.parse(dataIso).toLocalDateTime();
        } catch (DateTimeParseException e) {
            return LocalDateTime.now();
        }
    }

    private static String prepend(String existente, String linha) {
        if (existente == null || existente.isBlank()) return linha;
        return linha + "\n" + existente;
    }
}
