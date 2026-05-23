package com.mydelivery.admin.modulos.autocorrecao.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

/**
 * ÚNICO ponto de escrita do admin no banco principal.
 *
 * Regras inegociáveis pra esta classe:
 *  - Cada método é uma OPERAÇÃO ESPECÍFICA, com SQL hardcoded.
 *  - Nada de save() de entidade JPA. Nada de SQL dinâmico (concat/format).
 *  - Sempre PreparedStatement com parâmetros (JdbcTemplate.update já garante isso).
 *  - Sempre WHERE bem definido (com id e estado esperado), pra não atualizar
 *    múltiplas linhas sem querer.
 *  - Cada método retorna quantas linhas mexeu — chamador checa se foi 1.
 *  - Sem CASCADE. Sem DELETE. Sem ALTER TABLE.
 *
 * Toda escrita feita aqui aparece nos logs com prefixo {@code [MainDbWriter]}.
 *
 * Usa o {@code @Qualifier("mainTransactionManager")} pra rodar transação no DS correto.
 */
@Slf4j
@Component
public class MainDbWriter {

    private final JdbcTemplate jdbc;

    public MainDbWriter(@Qualifier("mainJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Bloqueia um restaurante cuja Trial expirou.
     *
     * Update condicional: só executa se {@code status='TRIAL'} ainda. Isso garante
     * idempotência (rodar 2x não duplica nada) e evita race com mudança manual
     * pelo dono do restaurante.
     *
     * @return número de linhas afetadas — 1 = sucesso, 0 = já não estava TRIAL (idempotente)
     */
    @Transactional(transactionManager = "mainTransactionManager")
    public int bloquearPorTrialExpirado(Long restauranteId, String motivo) {
        if (restauranteId == null) throw new IllegalArgumentException("restauranteId null");
        if (motivo == null || motivo.isBlank()) motivo = "Trial expirado (auto)";
        // limita pra não estourar a coluna (varchar tipicamente 255 no main)
        if (motivo.length() > 240) motivo = motivo.substring(0, 240);

        String sql = """
            UPDATE restaurantes
               SET status = 'BLOQUEADO',
                   bloqueado_em = ?,
                   motivo_bloqueio = ?
             WHERE id = ?
               AND status = 'TRIAL'
            """;

        int linhas = jdbc.update(sql, LocalDateTime.now(), motivo, restauranteId);
        log.warn("[MainDbWriter] bloquearPorTrialExpirado restauranteId={} linhas={}",
                restauranteId, linhas);
        return linhas;
    }

    // ─── ASSINATURAS (main) ──────────────────────────────────────────────

    /**
     * Suspende uma assinatura (status → INADIMPLENTE — usado pra pausa manual).
     * @return linhas afetadas
     */
    @Transactional(transactionManager = "mainTransactionManager")
    public int suspenderAssinatura(Long assinaturaId) {
        if (assinaturaId == null) throw new IllegalArgumentException("assinaturaId null");
        String sql = """
            UPDATE assinaturas
               SET status = 'INADIMPLENTE'
             WHERE id = ?
               AND status = 'ATIVA'
            """;
        int linhas = jdbc.update(sql, assinaturaId);
        log.warn("[MainDbWriter] suspenderAssinatura id={} linhas={}", assinaturaId, linhas);
        return linhas;
    }

    /** Reativa uma assinatura suspensa (INADIMPLENTE → ATIVA). */
    @Transactional(transactionManager = "mainTransactionManager")
    public int reativarAssinatura(Long assinaturaId) {
        if (assinaturaId == null) throw new IllegalArgumentException("assinaturaId null");
        String sql = """
            UPDATE assinaturas
               SET status = 'ATIVA'
             WHERE id = ?
               AND status = 'INADIMPLENTE'
            """;
        int linhas = jdbc.update(sql, assinaturaId);
        log.warn("[MainDbWriter] reativarAssinatura id={} linhas={}", assinaturaId, linhas);
        return linhas;
    }

    /** Cancela definitivo — registra cancelado_em e seta status. */
    @Transactional(transactionManager = "mainTransactionManager")
    public int cancelarAssinatura(Long assinaturaId) {
        if (assinaturaId == null) throw new IllegalArgumentException("assinaturaId null");
        String sql = """
            UPDATE assinaturas
               SET status = 'CANCELADA',
                   cancelado_em = ?
             WHERE id = ?
               AND status <> 'CANCELADA'
            """;
        int linhas = jdbc.update(sql, LocalDateTime.now(), assinaturaId);
        log.warn("[MainDbWriter] cancelarAssinatura id={} linhas={}", assinaturaId, linhas);
        return linhas;
    }

    // ─── PAGAMENTOS DE MENSALIDADE (main) ────────────────────────────────

    /**
     * Marca uma cobrança SaaS como paga manualmente (recebimento fora do gateway).
     * Atualiza também o registro de "última cobrança" e "próxima cobrança" da
     * assinatura associada — escopo limitado a 1 row.
     */
    @Transactional(transactionManager = "mainTransactionManager")
    public int marcarPagamentoMensalidadePago(Long pagamentoId, String metodo, String referencia) {
        if (pagamentoId == null) throw new IllegalArgumentException("pagamentoId null");
        if (metodo == null || metodo.isBlank()) metodo = "MANUAL";
        if (metodo.length() > 60) metodo = metodo.substring(0, 60);
        if (referencia != null && referencia.length() > 200) referencia = referencia.substring(0, 200);

        String sql = """
            UPDATE pagamentos_mensalidade
               SET status = 'PAGO',
                   pago_em = ?,
                   metodo_pagamento = ?,
                   referencia_gateway = COALESCE(?, referencia_gateway)
             WHERE id = ?
               AND status = 'PENDENTE'
            """;
        int linhas = jdbc.update(sql, LocalDateTime.now(), metodo, referencia, pagamentoId);
        log.warn("[MainDbWriter] marcarPagamentoMensalidadePago id={} linhas={}", pagamentoId, linhas);
        return linhas;
    }

    /** Cancela uma cobrança SaaS pendente (apaga PENDENTE → CANCELADO). */
    @Transactional(transactionManager = "mainTransactionManager")
    public int cancelarPagamentoMensalidade(Long pagamentoId) {
        if (pagamentoId == null) throw new IllegalArgumentException("pagamentoId null");
        String sql = """
            UPDATE pagamentos_mensalidade
               SET status = 'CANCELADO'
             WHERE id = ?
               AND status = 'PENDENTE'
            """;
        int linhas = jdbc.update(sql, pagamentoId);
        log.warn("[MainDbWriter] cancelarPagamentoMensalidade id={} linhas={}", pagamentoId, linhas);
        return linhas;
    }

    /**
     * Bloqueia um restaurante por inadimplência (fatura VENCIDA há > X dias).
     *
     * Update condicional: só se status='ATIVO' atualmente. Se já está BLOQUEADO
     * por outro motivo (trial expirado, manual), não sobrescreve.
     *
     * @return número de linhas afetadas — 1 = sucesso, 0 = idempotente / não era ATIVO
     */
    @Transactional(transactionManager = "mainTransactionManager")
    public int bloquearPorInadimplencia(Long restauranteId, String motivo) {
        if (restauranteId == null) throw new IllegalArgumentException("restauranteId null");
        if (motivo == null || motivo.isBlank()) motivo = "Inadimplência (auto)";
        if (motivo.length() > 240) motivo = motivo.substring(0, 240);

        String sql = """
            UPDATE restaurantes
               SET status = 'BLOQUEADO',
                   bloqueado_em = ?,
                   motivo_bloqueio = ?
             WHERE id = ?
               AND status = 'ATIVO'
            """;

        int linhas = jdbc.update(sql, LocalDateTime.now(), motivo, restauranteId);
        log.warn("[MainDbWriter] bloquearPorInadimplencia restauranteId={} linhas={}",
                restauranteId, linhas);
        return linhas;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SUPORTE — escritas em suporte_tickets / suporte_mensagens.
    // O admin gerencia tickets que foram abertos pelo restaurante no painel.
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Atualiza status + atualizado_em (e resolvido_em quando vai pra RESOLVIDO/FECHADO).
     * Status aceita: AGUARDANDO, EM_ATENDIMENTO, RESOLVIDO, FECHADO.
     */
    @Transactional(transactionManager = "mainTransactionManager")
    public int atualizarStatusTicket(Long ticketId, String novoStatus) {
        if (ticketId == null) throw new IllegalArgumentException("ticketId null");
        if (novoStatus == null) throw new IllegalArgumentException("status null");
        String s = novoStatus.toUpperCase();
        if (!s.matches("AGUARDANDO|EM_ATENDIMENTO|RESOLVIDO|FECHADO")) {
            throw new IllegalArgumentException("status inválido: " + novoStatus);
        }
        LocalDateTime agora = LocalDateTime.now();
        // resolvido_em é setado APENAS na primeira vez que vira RESOLVIDO/FECHADO.
        // Se reabrir (RESOLVIDO → AGUARDANDO), zeramos pra não mentir no histórico.
        String sql;
        int linhas;
        if ("RESOLVIDO".equals(s) || "FECHADO".equals(s)) {
            sql = "UPDATE suporte_tickets SET status = ?, atualizado_em = ?, "
                + "resolvido_em = COALESCE(resolvido_em, ?) WHERE id = ?";
            linhas = jdbc.update(sql, s, agora, agora, ticketId);
        } else {
            sql = "UPDATE suporte_tickets SET status = ?, atualizado_em = ?, resolvido_em = NULL WHERE id = ?";
            linhas = jdbc.update(sql, s, agora, ticketId);
        }
        log.info("[MainDbWriter] atualizarStatusTicket id={} status={} linhas={}",
                ticketId, s, linhas);
        return linhas;
    }

    /** Atualiza prioridade. Aceita: BAIXA, NORMAL, ALTA, URGENTE. */
    @Transactional(transactionManager = "mainTransactionManager")
    public int atualizarPrioridadeTicket(Long ticketId, String novaPrioridade) {
        if (ticketId == null) throw new IllegalArgumentException("ticketId null");
        String p = novaPrioridade == null ? "" : novaPrioridade.toUpperCase();
        if (!p.matches("BAIXA|NORMAL|ALTA|URGENTE")) {
            throw new IllegalArgumentException("prioridade inválida: " + novaPrioridade);
        }
        int linhas = jdbc.update(
                "UPDATE suporte_tickets SET prioridade = ?, atualizado_em = ? WHERE id = ?",
                p, LocalDateTime.now(), ticketId);
        log.info("[MainDbWriter] atualizarPrioridadeTicket id={} prioridade={} linhas={}",
                ticketId, p, linhas);
        return linhas;
    }

    /** Atualiza categoria (string livre — sem enum no banco principal). */
    @Transactional(transactionManager = "mainTransactionManager")
    public int atualizarCategoriaTicket(Long ticketId, String categoria) {
        if (ticketId == null) throw new IllegalArgumentException("ticketId null");
        if (categoria != null && categoria.length() > 40) categoria = categoria.substring(0, 40);
        int linhas = jdbc.update(
                "UPDATE suporte_tickets SET categoria = ?, atualizado_em = ? WHERE id = ?",
                categoria, LocalDateTime.now(), ticketId);
        log.info("[MainDbWriter] atualizarCategoriaTicket id={} categoria={} linhas={}",
                ticketId, categoria, linhas);
        return linhas;
    }

    /**
     * Atribui ou desatribui atendente. {@code atendenteId=null} = desatribui.
     * Não valida se o atendenteId existe — apenas grava (admin id é da tabela admin_user,
     * que não fica no DB principal).
     */
    @Transactional(transactionManager = "mainTransactionManager")
    public int atribuirAtendente(Long ticketId, Long atendenteId) {
        if (ticketId == null) throw new IllegalArgumentException("ticketId null");
        int linhas = jdbc.update(
                "UPDATE suporte_tickets SET atendente_id = ?, atualizado_em = ? WHERE id = ?",
                atendenteId, LocalDateTime.now(), ticketId);
        log.info("[MainDbWriter] atribuirAtendente ticketId={} atendenteId={} linhas={}",
                ticketId, atendenteId, linhas);
        return linhas;
    }

    /**
     * Insere mensagem do admin/atendente no thread. Devolve o ID gerado pra
     * permitir devolver DTO completo sem outro SELECT.
     */
    @Transactional(transactionManager = "mainTransactionManager")
    public Long inserirMensagemAdmin(Long ticketId, String autorNome, String texto) {
        if (ticketId == null) throw new IllegalArgumentException("ticketId null");
        if (texto == null || texto.isBlank()) throw new IllegalArgumentException("texto vazio");
        if (autorNome == null || autorNome.isBlank()) autorNome = "MyDelivery Suporte";
        if (autorNome.length() > 80) autorNome = autorNome.substring(0, 80);

        LocalDateTime agora = LocalDateTime.now();
        // INSERT + recuperar last_insert_id. Usamos KeyHolder do JdbcTemplate.
        var keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        final String autorNomeFinal = autorNome;
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO suporte_mensagens (ticket_id, autor, autor_nome, texto, criado_em) "
                  + "VALUES (?, 'ATENDENTE', ?, ?, ?)",
                    new String[]{"id"});
            ps.setLong(1, ticketId);
            ps.setString(2, autorNomeFinal);
            ps.setString(3, texto);
            ps.setObject(4, agora);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        Long id = key == null ? null : key.longValue();

        // Toda vez que admin responde, marca atualizado_em do ticket.
        jdbc.update("UPDATE suporte_tickets SET atualizado_em = ? WHERE id = ?",
                agora, ticketId);

        log.info("[MainDbWriter] inserirMensagemAdmin ticketId={} mensagemId={}", ticketId, id);
        return id;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PLANOS CATÁLOGO — CRUD na tabela planos_catalog
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Insere um novo plano. Devolve o ID gerado. Validações básicas no service;
     * aqui só aplicamos SQL com PreparedStatement.
     */
    @Transactional(transactionManager = "mainTransactionManager")
    public Long inserirPlanoCatalogo(String codigo, String nome, String descricao,
                                     java.math.BigDecimal valor, Integer duracaoMeses,
                                     Boolean recomendado, Boolean aceitaCartao, Boolean aceitaPix,
                                     String onboardingTipo, String featuresJson,
                                     Boolean ativo, Integer ordem) {
        if (codigo == null || codigo.isBlank()) throw new IllegalArgumentException("codigo vazio");
        if (nome == null || nome.isBlank()) throw new IllegalArgumentException("nome vazio");
        if (valor == null || valor.signum() < 0) throw new IllegalArgumentException("valor inválido");
        if (duracaoMeses == null || duracaoMeses <= 0) throw new IllegalArgumentException("duracaoMeses inválido");

        LocalDateTime agora = LocalDateTime.now();
        var keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        final String codigoFinal = codigo.trim().toUpperCase();
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO planos_catalog "
                  + "(codigo, nome, descricao, valor, duracao_meses, recomendado, aceita_cartao, "
                  + " aceita_pix, onboarding_tipo, features_json, ativo, ordem, criado_em, atualizado_em) "
                  + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new String[]{"id"});
            ps.setString(1, codigoFinal);
            ps.setString(2, nome);
            ps.setString(3, descricao);
            ps.setBigDecimal(4, valor);
            ps.setInt(5, duracaoMeses);
            ps.setBoolean(6, Boolean.TRUE.equals(recomendado));
            ps.setBoolean(7, aceitaCartao == null ? true : aceitaCartao);
            ps.setBoolean(8, aceitaPix == null ? true : aceitaPix);
            ps.setString(9, onboardingTipo);
            ps.setString(10, featuresJson);
            ps.setBoolean(11, ativo == null ? true : ativo);
            ps.setInt(12, ordem == null ? 0 : ordem);
            ps.setObject(13, agora);
            ps.setObject(14, agora);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        Long id = key == null ? null : key.longValue();
        log.info("[MainDbWriter] inserirPlanoCatalogo codigo={} id={}", codigoFinal, id);
        return id;
    }

    /**
     * Atualiza um plano existente. Não muda código (pra preservar referências).
     * Aceita null em qualquer campo — null = não altera (COALESCE no SQL).
     */
    @Transactional(transactionManager = "mainTransactionManager")
    public int atualizarPlanoCatalogo(Long id, String nome, String descricao,
                                      java.math.BigDecimal valor, Integer duracaoMeses,
                                      Boolean recomendado, Boolean aceitaCartao, Boolean aceitaPix,
                                      String onboardingTipo, String featuresJson,
                                      Boolean ativo, Integer ordem) {
        if (id == null) throw new IllegalArgumentException("id null");
        if (valor != null && valor.signum() < 0) throw new IllegalArgumentException("valor inválido");
        if (duracaoMeses != null && duracaoMeses <= 0) throw new IllegalArgumentException("duracaoMeses inválido");

        // Usamos COALESCE pra que null em qualquer parâmetro mantenha o valor atual.
        String sql = """
            UPDATE planos_catalog SET
                nome             = COALESCE(?, nome),
                descricao        = COALESCE(?, descricao),
                valor            = COALESCE(?, valor),
                duracao_meses    = COALESCE(?, duracao_meses),
                recomendado      = COALESCE(?, recomendado),
                aceita_cartao    = COALESCE(?, aceita_cartao),
                aceita_pix       = COALESCE(?, aceita_pix),
                onboarding_tipo  = COALESCE(?, onboarding_tipo),
                features_json    = COALESCE(?, features_json),
                ativo            = COALESCE(?, ativo),
                ordem            = COALESCE(?, ordem),
                atualizado_em    = ?
             WHERE id = ?
            """;
        int linhas = jdbc.update(sql,
                nome, descricao, valor, duracaoMeses,
                recomendado, aceitaCartao, aceitaPix,
                onboardingTipo, featuresJson, ativo, ordem,
                LocalDateTime.now(), id);
        log.info("[MainDbWriter] atualizarPlanoCatalogo id={} linhas={}", id, linhas);
        return linhas;
    }

    /**
     * Soft-delete (ativo=false). Não removemos a linha pra preservar histórico
     * de assinaturas que apontam pra esse plano.
     */
    @Transactional(transactionManager = "mainTransactionManager")
    public int desativarPlanoCatalogo(Long id) {
        if (id == null) throw new IllegalArgumentException("id null");
        int linhas = jdbc.update(
                "UPDATE planos_catalog SET ativo = false, atualizado_em = ? WHERE id = ?",
                LocalDateTime.now(), id);
        log.info("[MainDbWriter] desativarPlanoCatalogo id={} linhas={}", id, linhas);
        return linhas;
    }
}
