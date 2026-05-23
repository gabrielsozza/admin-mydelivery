package com.mydelivery.admin.modulos.tickets.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mydelivery.admin.modulos.auth.entity.AdminUser;
import com.mydelivery.admin.modulos.auth.repository.AdminUserRepository;
import com.mydelivery.admin.modulos.autocorrecao.service.MainDbWriter;
import com.mydelivery.admin.modulos.tickets.dto.MensagemDTO;
import com.mydelivery.admin.modulos.tickets.dto.MensagemRequest;
import com.mydelivery.admin.modulos.tickets.dto.TicketCreateRequest;
import com.mydelivery.admin.modulos.tickets.dto.TicketDetalheDTO;
import com.mydelivery.admin.modulos.tickets.dto.TicketListDTO;
import com.mydelivery.admin.modulos.tickets.dto.TicketUpdateRequest;
import com.mydelivery.admin.modulos.tickets.entity.Ticket;
import com.mydelivery.admin.modulos.tickets.entity.TicketMensagem;
import com.mydelivery.admin.shared.exception.NotFoundException;
import com.mydelivery.admin.shared.main.entity.RestauranteMain;
import com.mydelivery.admin.shared.main.entity.SuporteAnexoMain;
import com.mydelivery.admin.shared.main.entity.SuporteMensagemMain;
import com.mydelivery.admin.shared.main.entity.SuporteTicketMain;
import com.mydelivery.admin.shared.main.repository.RestauranteMainRepository;
import com.mydelivery.admin.shared.main.repository.SuporteAnexoMainRepository;
import com.mydelivery.admin.shared.main.repository.SuporteMensagemMainRepository;
import com.mydelivery.admin.shared.main.repository.SuporteTicketMainRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service de tickets do ADMIN — agora opera sobre os tickets REAIS do
 * banco principal ({@code mydelivery_db.suporte_tickets}), via mirror entities.
 *
 * Leituras: JPA nos mirrors {@link SuporteTicketMain} / {@link SuporteMensagemMain}.
 * Escritas: SQL hardcoded via {@link MainDbWriter} (convenção do projeto).
 *
 * A nomenclatura do DTO segue o padrão DO ADMIN (ABERTO/EM_ANDAMENTO/AGUARDANDO_CLIENTE)
 * por compatibilidade com o frontend já existente — convertemos pra/de
 * AGUARDANDO/EM_ATENDIMENTO/RESOLVIDO/FECHADO do banco principal.
 *
 * A criação via POST /api/admin/tickets ficou como NO-OP por enquanto
 * (admin não abre ticket em nome do restaurante na V1) — tickets nascem do
 * lado do restaurante via {@code /api/suporte/tickets}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private final SuporteTicketMainRepository ticketRepo;
    private final SuporteMensagemMainRepository mensagemRepo;
    private final SuporteAnexoMainRepository anexoRepo;
    private final AdminUserRepository adminRepo;
    private final RestauranteMainRepository restauranteRepo;
    private final MainDbWriter mainWriter;

    // ─── LISTAR ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "mainTransactionManager")
    public Page<TicketListDTO> listar(String statusStr,
                                      String prioridadeStr,
                                      String categoriaStr,
                                      Long atribuidoA,
                                      Long restauranteId,
                                      String q,
                                      int page, int size) {

        // Converte enum do admin (ABERTO/EM_ANDAMENTO…) pro enum do main (AGUARDANDO/EM_ATENDIMENTO…)
        SuporteTicketMain.Status status = adminStatusParaMain(statusStr);
        SuporteTicketMain.Prioridade prio = adminPrioridadeParaMain(prioridadeStr);
        String categoria = (categoriaStr == null || categoriaStr.isBlank()) ? null : categoriaStr.trim();

        if (size <= 0 || size > 100) size = 25;
        if (page < 0) page = 0;
        Pageable pageable = PageRequest.of(page, size);

        Page<SuporteTicketMain> tickets = ticketRepo.buscar(
                status, prio, categoria, atribuidoA, restauranteId,
                q == null || q.isBlank() ? null : q.trim(), pageable);

        Map<Long, String> restNomes = nomesRestaurantes(tickets.map(SuporteTicketMain::getRestauranteId).toList());
        Map<Long, String> adminNomes = nomesAdmins(tickets.map(SuporteTicketMain::getAtendenteId).toList());

        return tickets.map(t -> toListDTO(t,
                restNomes.get(t.getRestauranteId()),
                t.getAtendenteId() == null ? null : adminNomes.get(t.getAtendenteId())));
    }

    // ─── DETALHE ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "mainTransactionManager")
    public TicketDetalheDTO detalhe(Long id) {
        SuporteTicketMain t = ticketRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Ticket não encontrado"));

        List<SuporteMensagemMain> msgs = mensagemRepo.findByTicketIdOrderByCriadoEmAsc(id);
        // Carrega TODOS os anexos em 1 query (evita N+1) e agrupa por mensagemId
        List<Long> msgIds = msgs.stream().map(SuporteMensagemMain::getId).toList();
        Map<Long, List<String>> anexosPorMsg = new HashMap<>();
        if (!msgIds.isEmpty()) {
            for (SuporteAnexoMain a : anexoRepo.findByMensagemIdIn(msgIds)) {
                anexosPorMsg.computeIfAbsent(a.getMensagemId(), k -> new ArrayList<>())
                        .add(a.getUrl());
            }
        }
        List<MensagemDTO> mdtos = msgs.stream()
                .map(m -> toMensagemDTO(m, anexosPorMsg.getOrDefault(m.getId(), new ArrayList<>())))
                .toList();

        String restNome = nomeRestaurante(t.getRestauranteId());
        String atribuidoNome = t.getAtendenteId() == null ? null : nomeAdmin(t.getAtendenteId());

        return toDetalheDTO(t, restNome, atribuidoNome, mdtos);
    }

    // ─── CRIAR ──────────────────────────────────────────────────────────────
    // V1: admin não cria tickets. Ticket nasce no restaurante via /api/suporte/tickets.
    // Mantemos o método pra não quebrar o controller — devolve erro claro.

    public TicketDetalheDTO criar(TicketCreateRequest req) {
        throw new UnsupportedOperationException(
                "Criação de ticket pelo admin não disponível nesta versão. "
              + "O ticket deve ser aberto pelo restaurante no painel.");
    }

    // ─── ATUALIZAR (status/prioridade/categoria/atribuição) ────────────────

    @Transactional
    public TicketDetalheDTO atualizar(Long id, TicketUpdateRequest req) {
        // Confere que existe (e que conseguimos ler) antes de qualquer escrita.
        ticketRepo.findById(id).orElseThrow(() -> new NotFoundException("Ticket não encontrado"));

        if (req.getStatus() != null) {
            mainWriter.atualizarStatusTicket(id, adminStatusParaMainStr(req.getStatus().name()));
        }
        if (req.getPrioridade() != null) {
            mainWriter.atualizarPrioridadeTicket(id, adminPrioridadeParaMainStr(req.getPrioridade().name()));
        }
        if (req.getCategoria() != null) {
            // Categoria no main é string livre — guarda o name() do enum admin pra preservar info
            mainWriter.atualizarCategoriaTicket(id, req.getCategoria().name().toLowerCase());
        }
        if (req.getAtribuidoA() != null) {
            Long atendenteId = req.getAtribuidoA() == -1L ? null : req.getAtribuidoA();
            mainWriter.atribuirAtendente(id, atendenteId);
        }

        return detalhe(id);
    }

    // ─── ADICIONAR MENSAGEM ────────────────────────────────────────────────

    @Transactional
    public MensagemDTO adicionarMensagem(Long ticketId, MensagemRequest req) {
        ticketRepo.findById(ticketId).orElseThrow(() -> new NotFoundException("Ticket não encontrado"));

        AdminUser admin = currentAdminOrThrow();
        String autorNome = admin.getNome() != null ? admin.getNome() : admin.getEmail();
        String texto = req.getMensagem() == null ? "" : req.getMensagem().trim();
        if (texto.isEmpty()) throw new IllegalArgumentException("Mensagem vazia");

        Long mensagemId = mainWriter.inserirMensagemAdmin(ticketId, autorNome, texto);

        // Admin respondeu — se ninguém ainda atribuído, vira o admin atual.
        SuporteTicketMain t = ticketRepo.findById(ticketId).orElseThrow();
        if (t.getAtendenteId() == null && admin.getId() != null) {
            mainWriter.atribuirAtendente(ticketId, admin.getId());
        }
        // Status: se estava AGUARDANDO (= ABERTO no admin), passa pra EM_ATENDIMENTO
        if (t.getStatus() == SuporteTicketMain.Status.AGUARDANDO) {
            mainWriter.atualizarStatusTicket(ticketId, "EM_ATENDIMENTO");
        }

        // Devolve DTO da mensagem recém-criada — busca pra pegar criado_em real do DB
        SuporteMensagemMain criada = mensagemRepo.findById(mensagemId).orElse(null);
        return criada != null ? toMensagemDTO(criada) : MensagemDTO.builder()
                .id(mensagemId)
                .autorTipo("ADMIN")
                .autorId(admin.getId())
                .autorNome(autorNome)
                .mensagem(texto)
                .anexos(new ArrayList<>())
                .lidaPeloAdmin(true)
                .lidaPeloRestaurante(false)
                .build();
    }

    // ─── KPI ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "mainTransactionManager")
    public long countAbertos() {
        return ticketRepo.countAbertos();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MAPEAMENTOS DTO + ENUMS (Admin ↔ Main)
    // ═══════════════════════════════════════════════════════════════════════

    /** Status: enum do admin → enum do main. Null pra valor inválido (filtro vira "ignora"). */
    private SuporteTicketMain.Status adminStatusParaMain(String adminStatus) {
        if (adminStatus == null || adminStatus.isBlank()) return null;
        return switch (adminStatus.trim().toUpperCase()) {
            case "ABERTO"             -> SuporteTicketMain.Status.AGUARDANDO;
            case "EM_ANDAMENTO"       -> SuporteTicketMain.Status.EM_ATENDIMENTO;
            // "AGUARDANDO_CLIENTE" não tem equivalente exato no main — tratamos como EM_ATENDIMENTO
            case "AGUARDANDO_CLIENTE" -> SuporteTicketMain.Status.EM_ATENDIMENTO;
            case "RESOLVIDO"          -> SuporteTicketMain.Status.RESOLVIDO;
            case "FECHADO"            -> SuporteTicketMain.Status.FECHADO;
            default -> null;
        };
    }

    /** Variante string — pra MainDbWriter (que aceita string com regex). */
    private String adminStatusParaMainStr(String adminStatus) {
        SuporteTicketMain.Status s = adminStatusParaMain(adminStatus);
        return s == null ? null : s.name();
    }

    /** Inverso: status do main (DB) → status do admin (DTO/frontend). */
    private String mainStatusParaAdminStr(SuporteTicketMain.Status mainStatus) {
        if (mainStatus == null) return null;
        return switch (mainStatus) {
            case AGUARDANDO     -> "ABERTO";
            case EM_ATENDIMENTO -> "EM_ANDAMENTO";
            case RESOLVIDO      -> "RESOLVIDO";
            case FECHADO        -> "FECHADO";
        };
    }

    private SuporteTicketMain.Prioridade adminPrioridadeParaMain(String adminPrio) {
        if (adminPrio == null || adminPrio.isBlank()) return null;
        return switch (adminPrio.trim().toUpperCase()) {
            case "BAIXA"   -> SuporteTicketMain.Prioridade.BAIXA;
            case "MEDIA"   -> SuporteTicketMain.Prioridade.NORMAL;
            case "ALTA"    -> SuporteTicketMain.Prioridade.ALTA;
            case "CRITICA" -> SuporteTicketMain.Prioridade.URGENTE;
            default -> null;
        };
    }

    private String adminPrioridadeParaMainStr(String adminPrio) {
        SuporteTicketMain.Prioridade p = adminPrioridadeParaMain(adminPrio);
        return p == null ? null : p.name();
    }

    private String mainPrioridadeParaAdminStr(SuporteTicketMain.Prioridade p) {
        if (p == null) return null;
        return switch (p) {
            case BAIXA    -> "BAIXA";
            case NORMAL   -> "MEDIA";
            case ALTA     -> "ALTA";
            case URGENTE  -> "CRITICA";
        };
    }

    /** Categoria do main é STRING livre — devolve maiúscula pra bater com enum admin (ou OUTRO). */
    private String mainCategoriaParaAdminStr(String catMain) {
        if (catMain == null || catMain.isBlank()) return "OUTRO";
        String up = catMain.trim().toUpperCase();
        if (up.matches("PAGAMENTO|WHATSAPP|CARDAPIO|PEDIDO|FATURAMENTO|OUTRO")) return up;
        return "OUTRO";
    }

    /** Tipo do autor no DB main: RESTAURANTE/SISTEMA/ATENDENTE. Frontend admin espera autorTipo string. */
    private String mainAutorParaAdminStr(SuporteMensagemMain.Autor a) {
        if (a == null) return "SISTEMA";
        return switch (a) {
            case RESTAURANTE -> "RESTAURANTE";
            case SISTEMA     -> "SISTEMA";
            case ATENDENTE   -> "ADMIN";
        };
    }

    private TicketListDTO toListDTO(SuporteTicketMain t, String restNome, String atribuidoNome) {
        return TicketListDTO.builder()
                .id(t.getId())
                .restauranteId(t.getRestauranteId())
                .restauranteNome(restNome)
                .titulo(t.getAssunto())
                .status(mainStatusParaAdminStr(t.getStatus()))
                .prioridade(mainPrioridadeParaAdminStr(t.getPrioridade()))
                .categoria(mainCategoriaParaAdminStr(t.getCategoria()))
                .atribuidoA(t.getAtendenteId())
                .atribuidoNome(atribuidoNome)
                .criadoEm(t.getCriadoEm())
                .atualizadoEm(t.getAtualizadoEm())
                .build();
    }

    private TicketDetalheDTO toDetalheDTO(SuporteTicketMain t, String restNome, String atribuidoNome,
                                          List<MensagemDTO> msgs) {
        // No main, "descricao" é a primeira mensagem (do restaurante). Pegamos pra preencher o campo.
        String descricao = msgs.stream().findFirst().map(MensagemDTO::getMensagem).orElse(t.getAssunto());

        return TicketDetalheDTO.builder()
                .id(t.getId())
                .restauranteId(t.getRestauranteId())
                .restauranteNome(restNome)
                .titulo(t.getAssunto())
                .descricao(descricao)
                .status(mainStatusParaAdminStr(t.getStatus()))
                .prioridade(mainPrioridadeParaAdminStr(t.getPrioridade()))
                .categoria(mainCategoriaParaAdminStr(t.getCategoria()))
                .criadoPorAdminId(null)
                .criadoPorNome(restNome) // ticket veio do restaurante
                .atribuidoA(t.getAtendenteId())
                .atribuidoNome(atribuidoNome)
                .criadoEm(t.getCriadoEm())
                .atualizadoEm(t.getAtualizadoEm())
                .fechadoEm(t.getResolvidoEm())
                .mensagens(msgs)
                .build();
    }

    private MensagemDTO toMensagemDTO(SuporteMensagemMain m) {
        return toMensagemDTO(m, new ArrayList<>());
    }

    private MensagemDTO toMensagemDTO(SuporteMensagemMain m, List<String> anexos) {
        return MensagemDTO.builder()
                .id(m.getId())
                .autorTipo(mainAutorParaAdminStr(m.getAutor()))
                .autorId(null)
                .autorNome(m.getAutorNome())
                .mensagem(m.getTexto())
                .anexos(anexos == null ? new ArrayList<>() : anexos)
                .criadoEm(m.getCriadoEm())
                .lidaPeloAdmin(true)
                .lidaPeloRestaurante(true)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPERS — referências cruzadas (nomes de restaurantes/admins)
    // ═══════════════════════════════════════════════════════════════════════

    private AdminUser currentAdminOrThrow() {
        var authn = SecurityContextHolder.getContext().getAuthentication();
        if (authn == null || authn.getName() == null) {
            throw new IllegalStateException("Admin não autenticado");
        }
        return adminRepo.findByEmailIgnoreCase(authn.getName())
                .orElseThrow(() -> new IllegalStateException("Admin do token não existe mais"));
    }

    private String nomeRestaurante(Long id) {
        if (id == null) return null;
        return restauranteRepo.findById(id).map(RestauranteMain::getNome).orElse(null);
    }

    private Map<Long, String> nomesRestaurantes(List<Long> ids) {
        List<Long> distintos = ids.stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (distintos.isEmpty()) return Map.of();
        Map<Long, String> out = new HashMap<>();
        restauranteRepo.findAllById(distintos).forEach(r -> out.put(r.getId(), r.getNome()));
        return out;
    }

    private String nomeAdmin(Long id) {
        if (id == null) return null;
        return adminRepo.findById(id)
                .map(a -> a.getNome() != null ? a.getNome() : a.getEmail())
                .orElse(null);
    }

    private Map<Long, String> nomesAdmins(List<Long> ids) {
        List<Long> distintos = ids.stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (distintos.isEmpty()) return Map.of();
        Map<Long, String> out = new HashMap<>();
        adminRepo.findAllById(distintos).forEach(a ->
                out.put(a.getId(), a.getNome() != null ? a.getNome() : a.getEmail()));
        return out;
    }

    // ─── Tickets internos do admin (entity legada — não usadas mais por essa V1) ───
    // As classes Ticket / TicketMensagem ficam no projeto mas sem uso ativo.
    // Mantidas pra não invalidar migrations passadas; podem ser removidas num próximo PR.
    @SuppressWarnings("unused")
    private static void __legacyPlaceholder() { Ticket.class.getName(); TicketMensagem.class.getName(); }
}
