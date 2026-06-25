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
     * Aceita TRIAL, ATIVA e PENDENTE — só não mexe se já estiver CANCELADA
     * ou já INADIMPLENTE (idempotente).
     * @return linhas afetadas
     */
    @Transactional(transactionManager = "mainTransactionManager")
    public int suspenderAssinatura(Long assinaturaId) {
        if (assinaturaId == null) throw new IllegalArgumentException("assinaturaId null");
        String sql = """
            UPDATE assinaturas
               SET status = 'INADIMPLENTE'
             WHERE id = ?
               AND status NOT IN ('CANCELADA', 'INADIMPLENTE')
            """;
        int linhas = jdbc.update(sql, assinaturaId);
        log.warn("[MainDbWriter] suspenderAssinatura id={} linhas={}", assinaturaId, linhas);
        return linhas;
    }

    /**
     * Redefine senha do dono do restaurante (uso emergencial — suporte).
     * Atualiza diretamente o senha_hash do usuario associado.
     * Também apaga password_reset_tokens pendentes do mesmo usuário (evita confusão).
     *
     * @param restauranteId id do restaurante
     * @param novaSenhaHashBcrypt senha JÁ HASHEADA (BCrypt) — feita no service
     * @return 1 se atualizou, 0 se restaurante não tem usuario_id
     */
    @Transactional(transactionManager = "mainTransactionManager")
    public int redefinirSenhaDoRestaurante(Long restauranteId, String novaSenhaHashBcrypt) {
        if (restauranteId == null) throw new IllegalArgumentException("restauranteId null");
        if (novaSenhaHashBcrypt == null || novaSenhaHashBcrypt.isBlank()) {
            throw new IllegalArgumentException("senha hash vazia");
        }
        if (!novaSenhaHashBcrypt.startsWith("$2")) {
            // Sanity check: BCrypt sempre começa com $2a/$2b/$2y
            throw new IllegalArgumentException("hash inválido (precisa ser BCrypt)");
        }

        Long usuarioId;
        try {
            usuarioId = jdbc.queryForObject(
                "SELECT usuario_id FROM restaurantes WHERE id = ?",
                Long.class, restauranteId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new IllegalStateException("Restaurante " + restauranteId + " não encontrado");
        }
        if (usuarioId == null) return 0;

        int linhas = jdbc.update(
            "UPDATE usuarios SET senha_hash = ? WHERE id = ?",
            novaSenhaHashBcrypt, usuarioId);

        // Limpa tokens pendentes de recuperação (segurança)
        try {
            jdbc.update("DELETE FROM password_reset_tokens WHERE usuario_id = ?", usuarioId);
        } catch (Exception e) {
            log.warn("[MainDbWriter] não consegui limpar reset tokens do usuario={}: {}", usuarioId, e.getMessage());
        }

        log.warn("[MainDbWriter] REDEFINIR SENHA restauranteId={} usuarioId={} linhas={}",
                restauranteId, usuarioId, linhas);
        return linhas;
    }

    /**
     * Bloqueia restaurante manualmente (admin) + marca assinatura como INADIMPLENTE.
     * Aceita restaurante em qualquer status (ATIVO/TRIAL/PENDENTE) — só não sobrescreve
     * se já está BLOQUEADO.
     * @return número total de updates feitos (restaurante + assinatura)
     */
    @Transactional(transactionManager = "mainTransactionManager")
    public int suspenderRestauranteManual(Long restauranteId, String motivo) {
        if (restauranteId == null) throw new IllegalArgumentException("restauranteId null");
        if (motivo == null || motivo.isBlank()) motivo = "Suspenso manualmente pelo admin";
        if (motivo.length() > 240) motivo = motivo.substring(0, 240);

        int total = 0;
        // 1. Bloqueia restaurante
        total += jdbc.update("""
            UPDATE restaurantes
               SET status = 'BLOQUEADO',
                   bloqueado_em = ?,
                   motivo_bloqueio = ?
             WHERE id = ?
               AND status <> 'BLOQUEADO'
            """, LocalDateTime.now(), motivo, restauranteId);

        // 2. Marca assinatura ativa como INADIMPLENTE (se existir)
        total += jdbc.update("""
            UPDATE assinaturas
               SET status = 'INADIMPLENTE'
             WHERE restaurante_id = ?
               AND status NOT IN ('CANCELADA', 'INADIMPLENTE')
            """, restauranteId);

        log.warn("[MainDbWriter] suspenderRestauranteManual id={} updates={} motivo={}",
                restauranteId, total, motivo);
        return total;
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

    // ═══════════════════════════════════════════════════════════════════════
    // HARD DELETE — apaga restaurante e TUDO relacionado
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Apaga completamente um restaurante e todos os dados associados.
     *
     * Uso: limpar cadastros "lixo" de curiosos que criam conta e nunca usam.
     * ATENÇÃO: operação IRREVERSÍVEL. Não recupera. Sem soft-delete.
     *
     * Ordem dos DELETEs respeita a topologia de foreign keys: filhos primeiro.
     * Tudo dentro de uma transação — se qualquer step falhar, rollback total.
     *
     * Tabelas afetadas (em ordem):
     *  1. pedido_item_complementos → pedido_itens → pagamentos → pedidos
     *  2. cupons_usos → cupons
     *  3. compra_itens → compras
     *  4. complementos_item → complementos_grupo → fichas_tecnicas → produtos → categorias
     *  5. movimentacoes_estoque → insumos
     *  6. chamadas_garcom → mesas
     *  7. pontos_transacoes → programas_fidelidade → carrinhos_abandonados → clientes
     *  8. entregadores
     *  9. pagamentos_mensalidade → assinaturas
     * 10. suporte_anexos → suporte_mensagens → suporte_tickets
     * 11. whatsapp_instances
     * 12. configuracoes_restaurante + restaurante_bairros/modos/pagamentos/slots
     * 13. restaurantes
     * 14. password_reset_tokens (do usuário dono) + usuario órfão
     *
     * @return Map com contadores de linhas removidas por área pra logging/auditoria
     */
    @Transactional(transactionManager = "mainTransactionManager")
    public java.util.Map<String, Integer> apagarRestauranteCompletamente(Long restauranteId) {
        if (restauranteId == null) throw new IllegalArgumentException("restauranteId null");

        // Captura o usuario_id ANTES de apagar o restaurante (ficaria órfão senão)
        Long usuarioId;
        try {
            usuarioId = jdbc.queryForObject(
                "SELECT usuario_id FROM restaurantes WHERE id = ?",
                Long.class, restauranteId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new IllegalStateException("Restaurante " + restauranteId + " não encontrado");
        }

        java.util.Map<String, Integer> stats = new java.util.LinkedHashMap<>();

        // ─── 1. PEDIDOS (cascata profunda) ──────────────────────────────
        stats.put("pedido_item_complementos", jdbc.update("""
            DELETE pic FROM pedido_item_complementos pic
              JOIN pedido_itens pi ON pi.id = pic.pedido_item_id
              JOIN pedidos p ON p.id = pi.pedido_id
             WHERE p.restaurante_id = ?
            """, restauranteId));

        stats.put("pedido_itens", jdbc.update("""
            DELETE pi FROM pedido_itens pi
              JOIN pedidos p ON p.id = pi.pedido_id
             WHERE p.restaurante_id = ?
            """, restauranteId));

        stats.put("pagamentos", jdbc.update("""
            DELETE pg FROM pagamentos pg
              JOIN pedidos p ON p.id = pg.pedido_id
             WHERE p.restaurante_id = ?
            """, restauranteId));

        stats.put("pedidos", jdbc.update(
            "DELETE FROM pedidos WHERE restaurante_id = ?", restauranteId));

        // ─── 2. CUPONS ──────────────────────────────────────────────────
        stats.put("cupons_usos", jdbc.update("""
            DELETE cu FROM cupons_usos cu
              JOIN cupons c ON c.id = cu.cupom_id
             WHERE c.restaurante_id = ?
            """, restauranteId));

        // cupom_modos = @ElementCollection do Cupom.modosAplicaveis. FK pra cupons
        // sem ON DELETE CASCADE no schema gerado pelo Hibernate — precisa
        // limpar manualmente antes do DELETE FROM cupons, senão estoura
        // FK constraint (railway.cupom_modos / FKpkrglgquw6ipdbuk7ui3nw2h4).
        stats.put("cupom_modos", jdbc.update("""
            DELETE cm FROM cupom_modos cm
              JOIN cupons c ON c.id = cm.cupom_id
             WHERE c.restaurante_id = ?
            """, restauranteId));

        stats.put("cupons", jdbc.update(
            "DELETE FROM cupons WHERE restaurante_id = ?", restauranteId));

        // ─── 3. COMPRAS (estoque) ───────────────────────────────────────
        stats.put("compra_itens", jdbc.update("""
            DELETE ci FROM compra_itens ci
              JOIN compras c ON c.id = ci.compra_id
             WHERE c.restaurante_id = ?
            """, restauranteId));

        stats.put("compras", jdbc.update(
            "DELETE FROM compras WHERE restaurante_id = ?", restauranteId));

        // ─── 4. PRODUTOS (complementos, ficha técnica, categorias) ──────
        // banners.produto_id → produtos.id sem ON DELETE CASCADE. Limpa antes
        // pra não estourar "FKfqctpljepw8fc2ag42bv5iaya" no DELETE FROM produtos.
        stats.put("banners", jdbc.update(
            "DELETE FROM banners WHERE restaurante_id = ?", restauranteId));

        stats.put("complementos_item", jdbc.update("""
            DELETE ci FROM complementos_item ci
              JOIN complementos_grupo cg ON cg.id = ci.grupo_id
              JOIN produtos p ON p.id = cg.produto_id
             WHERE p.restaurante_id = ?
            """, restauranteId));

        stats.put("complementos_grupo", jdbc.update("""
            DELETE cg FROM complementos_grupo cg
              JOIN produtos p ON p.id = cg.produto_id
             WHERE p.restaurante_id = ?
            """, restauranteId));

        stats.put("fichas_tecnicas", jdbc.update("""
            DELETE ft FROM fichas_tecnicas ft
              JOIN produtos p ON p.id = ft.produto_id
             WHERE p.restaurante_id = ?
            """, restauranteId));

        stats.put("produtos", jdbc.update(
            "DELETE FROM produtos WHERE restaurante_id = ?", restauranteId));

        stats.put("categorias", jdbc.update(
            "DELETE FROM categorias WHERE restaurante_id = ?", restauranteId));

        // ─── 5. ESTOQUE (insumos + movimentações) ───────────────────────
        stats.put("movimentacoes_estoque", jdbc.update("""
            DELETE me FROM movimentacoes_estoque me
              JOIN insumos i ON i.id = me.insumo_id
             WHERE i.restaurante_id = ?
            """, restauranteId));

        stats.put("insumos", jdbc.update(
            "DELETE FROM insumos WHERE restaurante_id = ?", restauranteId));

        // ─── 6. MESAS / CHAMADAS GARÇOM (QR codes) ──────────────────────
        stats.put("chamadas_garcom", jdbc.update(
            "DELETE FROM chamadas_garcom WHERE restaurante_id = ?", restauranteId));

        // mesa_sessoes.mesa_id → mesas.id (FK sem ON DELETE CASCADE).
        // Sem isso, o DELETE FROM mesas estourava
        // "FKbe9hnm31j17jct3r6218rxcr9". A coluna restaurante_id em
        // mesa_sessoes e' denormalizada justamente pra esse cleanup
        // ser direto, sem JOIN.
        stats.put("mesa_sessoes", jdbc.update(
            "DELETE FROM mesa_sessoes WHERE restaurante_id = ?", restauranteId));

        stats.put("mesas", jdbc.update(
            "DELETE FROM mesas WHERE restaurante_id = ?", restauranteId));

        // ─── 7. CLIENTES / FIDELIDADE / CARRINHO ────────────────────────
        stats.put("pontos_transacoes", jdbc.update(
            "DELETE FROM pontos_transacoes WHERE restaurante_id = ?", restauranteId));

        stats.put("programas_fidelidade", jdbc.update(
            "DELETE FROM programas_fidelidade WHERE restaurante_id = ?", restauranteId));

        stats.put("carrinhos_abandonados", jdbc.update(
            "DELETE FROM carrinhos_abandonados WHERE restaurante_id = ?", restauranteId));

        stats.put("clientes", jdbc.update(
            "DELETE FROM clientes WHERE restaurante_id = ?", restauranteId));

        // ─── 8. ENTREGADORES ────────────────────────────────────────────
        stats.put("entregadores", jdbc.update(
            "DELETE FROM entregadores WHERE restaurante_id = ?", restauranteId));

        // ─── 9. FATURAMENTO (SaaS interno) ──────────────────────────────
        stats.put("pagamentos_mensalidade", jdbc.update(
            "DELETE FROM pagamentos_mensalidade WHERE restaurante_id = ?", restauranteId));

        stats.put("assinaturas", jdbc.update(
            "DELETE FROM assinaturas WHERE restaurante_id = ?", restauranteId));

        // ─── 10. SUPORTE ────────────────────────────────────────────────
        stats.put("suporte_anexos", jdbc.update("""
            DELETE sa FROM suporte_anexos sa
              JOIN suporte_mensagens sm ON sm.id = sa.mensagem_id
              JOIN suporte_tickets st ON st.id = sm.ticket_id
             WHERE st.restaurante_id = ?
            """, restauranteId));

        stats.put("suporte_mensagens", jdbc.update("""
            DELETE sm FROM suporte_mensagens sm
              JOIN suporte_tickets st ON st.id = sm.ticket_id
             WHERE st.restaurante_id = ?
            """, restauranteId));

        stats.put("suporte_tickets", jdbc.update(
            "DELETE FROM suporte_tickets WHERE restaurante_id = ?", restauranteId));

        // ─── 11. WHATSAPP ───────────────────────────────────────────────
        // Ordem topológica respeitando FKs:
        //  - whatsapp_acoes_automaticas → FK pra whatsapp_incidentes E whatsapp_instances
        //  - whatsapp_health_log        → FK pra whatsapp_instances
        //  - whatsapp_incidentes        → FK pra whatsapp_instances
        //  - whatsapp_instances         → linha-mãe
        //
        // Antes só o último DELETE rodava e a FK estourava com:
        //   "Cannot delete or update a parent row: a foreign key constraint
        //    fails (whatsapp_health_log/.../incidentes)".
        // O JOIN amarra o filho à instância via instance_id pra filtrar só
        // os registros desse restaurante.
        stats.put("whatsapp_acoes_automaticas", jdbc.update("""
            DELETE wa FROM whatsapp_acoes_automaticas wa
              JOIN whatsapp_instances wi ON wi.id = wa.instance_id
             WHERE wi.restaurante_id = ?
            """, restauranteId));

        stats.put("whatsapp_health_log", jdbc.update("""
            DELETE hl FROM whatsapp_health_log hl
              JOIN whatsapp_instances wi ON wi.id = hl.instance_id
             WHERE wi.restaurante_id = ?
            """, restauranteId));

        stats.put("whatsapp_incidentes", jdbc.update("""
            DELETE wi2 FROM whatsapp_incidentes wi2
              JOIN whatsapp_instances wi ON wi.id = wi2.instance_id
             WHERE wi.restaurante_id = ?
            """, restauranteId));

        stats.put("whatsapp_instances", jdbc.update(
            "DELETE FROM whatsapp_instances WHERE restaurante_id = ?", restauranteId));

        // ─── 12. CONFIG + ELEMENT COLLECTIONS ───────────────────────────
        stats.put("configuracoes_restaurante", jdbc.update(
            "DELETE FROM configuracoes_restaurante WHERE restaurante_id = ?", restauranteId));

        stats.put("restaurante_bairros", jdbc.update(
            "DELETE FROM restaurante_bairros WHERE restaurante_id = ?", restauranteId));

        stats.put("restaurante_modos", jdbc.update(
            "DELETE FROM restaurante_modos WHERE restaurante_id = ?", restauranteId));

        stats.put("restaurante_pagamentos", jdbc.update(
            "DELETE FROM restaurante_pagamentos WHERE restaurante_id = ?", restauranteId));

        stats.put("restaurante_slots", jdbc.update(
            "DELETE FROM restaurante_slots WHERE restaurante_id = ?", restauranteId));

        // ─── 13. O RESTAURANTE EM SI ────────────────────────────────────
        stats.put("restaurantes", jdbc.update(
            "DELETE FROM restaurantes WHERE id = ?", restauranteId));

        // ─── 14. USUÁRIO DONO ───────────────────────────────────────────
        if (usuarioId != null) {
            stats.put("password_reset_tokens", jdbc.update(
                "DELETE FROM password_reset_tokens WHERE usuario_id = ?", usuarioId));
            stats.put("usuarios", jdbc.update(
                "DELETE FROM usuarios WHERE id = ?", usuarioId));
        }

        int total = stats.values().stream().mapToInt(Integer::intValue).sum();
        log.warn("[MainDbWriter] APAGAR restauranteId={} usuarioId={} totalLinhas={} detalhe={}",
                restauranteId, usuarioId, total, stats);
        return stats;
    }
}
