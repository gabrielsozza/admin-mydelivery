package com.mydelivery.admin.modulos.pagamentos.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mydelivery.admin.modulos.faturamento.entity.Fatura;
import com.mydelivery.admin.modulos.faturamento.repository.FaturaRepository;
import com.mydelivery.admin.modulos.pagamentos.dto.CobrancaBoletoDTO;
import com.mydelivery.admin.modulos.pagamentos.dto.CobrancaBoletoRequest;
import com.mydelivery.admin.modulos.pagamentos.dto.CobrancaPixDTO;
import com.mydelivery.admin.modulos.pagamentos.dto.MpPaymentResponse;
import com.mydelivery.admin.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gera cobranças MP pra uma fatura. Cada chamada cria um novo payment no MP
 * (mesmo se já houver um) — útil pra reemitir PIX vencido. O {@code externalReference}
 * sempre embute o faturaId, então o webhook consegue mapear de volta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CobrancaService {

    private final FaturaRepository faturaRepo;
    private final MercadoPagoAdminClient mp;

    @Value("${admin.mp.payer-default-email:billing@mydeliveryfood.com.br}")
    private String payerDefaultEmail;

    @Value("${admin.public-base-url:http://localhost:8090}")
    private String publicBaseUrl;

    @Transactional
    public CobrancaPixDTO criarPix(Long faturaId) {
        Fatura f = buscarFaturaCobravel(faturaId);

        String descricao = "MyDelivery — " + f.getPlanoNome() + " (comp. " + f.getCompetencia() + ")";
        MpPaymentResponse resp = mp.criarCobrancaPix(
                f.getValor(),
                descricao,
                payerDefaultEmail,
                externalReference(f),
                webhookUrl()
        );

        atualizarFaturaComMp(f, resp);

        var td = resp.getPointOfInteraction() != null ? resp.getPointOfInteraction().getTransactionData() : null;
        return CobrancaPixDTO.builder()
                .faturaId(f.getId())
                .mpPaymentId(resp.getId())
                .valor(f.getValor())
                .statusMp(resp.getStatus())
                .pixCopiaCola(td == null ? null : td.getQrCode())
                .qrCodeBase64(td == null ? null : td.getQrCodeBase64())
                .ticketUrl(td == null ? null : td.getTicketUrl())
                .build();
    }

    @Transactional
    public CobrancaBoletoDTO criarBoleto(Long faturaId, CobrancaBoletoRequest req) {
        Fatura f = buscarFaturaCobravel(faturaId);

        String email = req.getEmail() != null && !req.getEmail().isBlank()
                ? req.getEmail() : payerDefaultEmail;
        String descricao = "MyDelivery — " + f.getPlanoNome() + " (comp. " + f.getCompetencia() + ")";

        MpPaymentResponse resp = mp.criarCobrancaBoleto(
                f.getValor(),
                descricao,
                email,
                somenteDigitos(req.getCpf()),
                req.getNome(),
                externalReference(f),
                webhookUrl()
        );

        atualizarFaturaComMp(f, resp);

        var td = resp.getPointOfInteraction() != null ? resp.getPointOfInteraction().getTransactionData() : null;
        return CobrancaBoletoDTO.builder()
                .faturaId(f.getId())
                .mpPaymentId(resp.getId())
                .valor(f.getValor())
                .statusMp(resp.getStatus())
                .ticketUrl(td == null ? null : td.getTicketUrl())
                .build();
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────

    private Fatura buscarFaturaCobravel(Long id) {
        Fatura f = faturaRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Fatura não encontrada"));
        if (f.getStatus() == Fatura.Status.PAGA) {
            throw new IllegalStateException("Fatura já paga — nada a cobrar");
        }
        if (f.getStatus() == Fatura.Status.CANCELADA) {
            throw new IllegalStateException("Fatura cancelada — não dá pra cobrar");
        }
        return f;
    }

    private void atualizarFaturaComMp(Fatura f, MpPaymentResponse resp) {
        f.setExternalPaymentId(String.valueOf(resp.getId()));
        f.setObservacao(prepend(f.getObservacao(),
                "[" + LocalDateTime.now() + "] Cobrança MP criada — payment " + resp.getId()
                        + " status=" + resp.getStatus()));
        faturaRepo.save(f);
    }

    static String externalReference(Fatura f) {
        return "fatura-" + f.getId();
    }

    /** Webhook url completa que o MP vai chamar pra notificar. */
    private String webhookUrl() {
        String base = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return base + "/api/admin/mp-webhook";
    }

    private static String prepend(String existente, String linha) {
        if (existente == null || existente.isBlank()) return linha;
        return linha + "\n" + existente;
    }

    private static String somenteDigitos(String s) {
        return s == null ? null : s.replaceAll("\\D", "");
    }
}
