package com.mydelivery.admin.modulos.configuracoes.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mydelivery.admin.modulos.configuracoes.dto.ConfiguracaoDTO;
import com.mydelivery.admin.modulos.configuracoes.entity.ConfiguracaoAdmin;
import com.mydelivery.admin.modulos.configuracoes.repository.ConfiguracaoAdminRepository;
import com.mydelivery.admin.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service de configurações dinâmicas do admin.
 *
 * No boot, registra as chaves esperadas se não existirem (sem sobrescrever).
 * Os getters expostos pra outros services consultarem o valor atual com fallback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfiguracaoAdminService {

    public static final String CHAVE_MP_ACCESS_TOKEN = "mp.access_token";
    public static final String CHAVE_MP_WEBHOOK_SECRET = "mp.webhook_secret";
    public static final String CHAVE_MP_PUBLIC_KEY = "mp.public_key";
    public static final String CHAVE_MP_AMBIENTE = "mp.ambiente";
    public static final String CHAVE_MP_PAYER_EMAIL = "mp.payer_default_email";

    private final ConfiguracaoAdminRepository repo;

    /** Registra as chaves esperadas no primeiro boot (não sobrescreve). */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        Map<String, String[]> defaults = new HashMap<>();
        defaults.put(CHAVE_MP_ACCESS_TOKEN,
                new String[]{ "Bearer do MP do admin (cobrança SaaS). Gere em https://www.mercadopago.com.br/developers", "true" });
        defaults.put(CHAVE_MP_WEBHOOK_SECRET,
                new String[]{ "Secret HMAC do webhook MP. Em ESTRITO bloqueia webhooks sem assinatura válida.", "true" });
        defaults.put(CHAVE_MP_PUBLIC_KEY,
                new String[]{ "Public Key MP (opcional, usado em integrações de front).", "false" });
        defaults.put(CHAVE_MP_AMBIENTE,
                new String[]{ "Ambiente MP: PROD ou TEST. Use TEST com credenciais sandbox.", "false" });
        defaults.put(CHAVE_MP_PAYER_EMAIL,
                new String[]{ "Email do pagador no payload MP (genérico, ex: billing@mydeliveryfood.com.br).", "false" });

        defaults.forEach((chave, meta) -> {
            if (repo.findByChave(chave).isEmpty()) {
                ConfiguracaoAdmin c = ConfiguracaoAdmin.builder()
                        .chave(chave)
                        .valor(null)
                        .descricao(meta[0])
                        .sensivel("true".equals(meta[1]))
                        .build();
                repo.save(c);
                log.info("[Config] chave '{}' criada (vazia) — preencha pelo painel /configuracoes", chave);
            }
        });
    }

    @Transactional(readOnly = true)
    public List<ConfiguracaoDTO> listar() {
        return repo.findAllByOrderByChaveAsc().stream().map(ConfiguracaoDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public ConfiguracaoDTO detalhe(String chave) {
        return repo.findByChave(chave).map(ConfiguracaoDTO::from)
                .orElseThrow(() -> new NotFoundException("Configuração '" + chave + "' não encontrada"));
    }

    @Transactional
    public ConfiguracaoDTO salvar(String chave, String valor) {
        if (chave == null || chave.isBlank()) {
            throw new IllegalArgumentException("Chave é obrigatória");
        }
        ConfiguracaoAdmin c = repo.findByChave(chave)
                .orElseThrow(() -> new NotFoundException("Configuração '" + chave + "' não cadastrada"));
        c.setValor(valor != null ? valor.trim() : null);
        repo.save(c);
        log.info("[Config] chave '{}' atualizada (preenchida={})", chave, c.getValor() != null && !c.getValor().isBlank());
        return ConfiguracaoDTO.from(c);
    }

    /** Valor atual ou fallback se vazio. Usado por outros services que dependem da config. */
    @Transactional(readOnly = true)
    public String obter(String chave, String fallback) {
        return repo.findByChave(chave)
                .map(ConfiguracaoAdmin::getValor)
                .filter(v -> v != null && !v.isBlank())
                .orElse(fallback);
    }
}
