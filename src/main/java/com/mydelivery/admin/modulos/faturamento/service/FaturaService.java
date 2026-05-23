package com.mydelivery.admin.modulos.faturamento.service;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mydelivery.admin.modulos.autocorrecao.service.MainDbWriter;
import com.mydelivery.admin.modulos.faturamento.dto.FaturaDTO;
import com.mydelivery.admin.modulos.faturamento.dto.FaturaMarcarPagaRequest;
import com.mydelivery.admin.modulos.faturamento.entity.Fatura;
import com.mydelivery.admin.shared.exception.NotFoundException;
import com.mydelivery.admin.shared.main.entity.PagamentoMensalidadeMain;
import com.mydelivery.admin.shared.main.repository.PagamentoMensalidadeMainRepository;
import com.mydelivery.admin.shared.main.repository.RestauranteMainRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Faturas SaaS — lê {@code pagamentos_mensalidade} do main DB.
 *
 * Mudanças vs versão anterior:
 *  - Tabela {@code fatura} do admin DB ficou shadow/legacy (não é mais lida nem
 *    escrita pelos endpoints).
 *  - Geração mensal automatizada NÃO está mais aqui — o main app gera as cobranças
 *    quando precisa (no checkout, na renovação). Admin só visualiza.
 *  - "Marcar paga" e "Cancelar" escrevem em {@code pagamentos_mensalidade} via
 *    {@link MainDbWriter}.
 *
 * Mapeamento status (main → DTO admin):
 *   PENDENTE → PENDENTE
 *   PAGO → PAGA
 *   CANCELADO → CANCELADA
 *   (não há VENCIDA no main — vence implicitamente quando passa próxima_cobranca)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaturaService {

    private static final DateTimeFormatter COMPETENCIA_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final PagamentoMensalidadeMainRepository repo;
    private final RestauranteMainRepository restauranteRepo;
    private final MainDbWriter writer;

    // ─── LISTAGEM ─────────────────────────────────────────────────────────

    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public Page<FaturaDTO> listar(String statusStr, Long restauranteId, String competencia,
                                  int page, int size) {
        PagamentoMensalidadeMain.Status status = mapearStatusBuscaToMain(statusStr);
        if (size <= 0 || size > 100) size = 25;
        if (page < 0) page = 0;
        Pageable pageable = PageRequest.of(page, size);

        Page<PagamentoMensalidadeMain> pagamentos = repo.buscar(status, restauranteId, pageable);
        Map<Long, String> nomes = nomesRestaurantes(pagamentos.map(PagamentoMensalidadeMain::getRestauranteId).toList());

        // Filtra por competência (yyyy-MM) em memória se vier — repository não suporta
        // diretamente (criado_em vs competencia). Pequeno trade-off.
        if (competencia != null && !competencia.isBlank()) {
            return pagamentos.map(p -> toDTO(p, nomes.get(p.getRestauranteId())))
                             .map(dto -> competencia.equals(dto.getCompetencia()) ? dto : null);
        }
        return pagamentos.map(p -> toDTO(p, nomes.get(p.getRestauranteId())));
    }

    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public FaturaDTO detalhe(Long id) {
        PagamentoMensalidadeMain p = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Fatura não encontrada"));
        return toDTO(p, nomeRestaurante(p.getRestauranteId()));
    }

    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public List<FaturaDTO> doRestaurante(Long restauranteId) {
        String nome = nomeRestaurante(restauranteId);
        return repo.findByRestauranteIdOrderByCriadoEmDesc(restauranteId).stream()
                .map(p -> toDTO(p, nome))
                .toList();
    }

    // ─── AÇÕES ────────────────────────────────────────────────────────────

    /**
     * Marca pagamento como PAGO no main DB. Para PIX recebido fora do MP, transferência
     * bancária, etc — o operador marca manualmente.
     */
    public FaturaDTO marcarPaga(Long id, FaturaMarcarPagaRequest req) {
        String metodo = req != null && req.getMetodoPagamento() != null
                ? req.getMetodoPagamento().name() : "MANUAL";
        String ref = req != null ? req.getExternalPaymentId() : null;
        int n = writer.marcarPagamentoMensalidadePago(id, metodo, ref);
        if (n == 0) {
            // Verifica se já tava paga / cancelada pra dar erro mais útil
            PagamentoMensalidadeMain p = repo.findById(id)
                    .orElseThrow(() -> new NotFoundException("Fatura não encontrada"));
            throw new IllegalStateException("Fatura já está " + p.getStatus() + " — não pode marcar como paga");
        }
        log.info("[Fatura] PAGA id={} metodo={}", id, metodo);
        return detalhe(id);
    }

    public FaturaDTO cancelar(Long id, String motivo) {
        int n = writer.cancelarPagamentoMensalidade(id);
        if (n == 0) {
            PagamentoMensalidadeMain p = repo.findById(id)
                    .orElseThrow(() -> new NotFoundException("Fatura não encontrada"));
            throw new IllegalStateException("Fatura está " + p.getStatus() + " — só PENDENTES podem ser canceladas");
        }
        log.info("[Fatura] cancelada id={} motivo={}", id, motivo);
        return detalhe(id);
    }

    // ─── ENDPOINTS DE GERAÇÃO/VENCIMENTO — DEPRECATED ─────────────────────

    /**
     * Geração mensal foi descontinuada — o main app gera cobranças sob demanda
     * (no checkout / renovação). Retorna resumo vazio pra não quebrar o scheduler
     * antigo.
     */
    public GeracaoResumo gerarFaturasDaCompetencia(LocalDate referencia) {
        String competencia = referencia.format(COMPETENCIA_FMT);
        log.info("[Fatura] gerarFaturasDaCompetencia chamado (no-op — main gera sob demanda)");
        return new GeracaoResumo(competencia, 0, 0, 0);
    }

    /** No-op — main controla vencimento automaticamente via Assinatura.proximaCobranca. */
    public int marcarVencidas() {
        return 0;
    }

    /**
     * Inadimplência crítica — agora baseada em {@code Assinatura.status = INADIMPLENTE}
     * no main. Foi movida pra MonitoramentoService idealmente, mas mantenho no-op
     * aqui pra compatibilidade com o scheduler existente.
     */
    public int detectarInadimplenciaCriticaEAcionarBloqueio(int diasTolerancia) {
        log.debug("[Fatura] detectarInadimplencia: no-op por enquanto (lógica vai migrar pro monitor)");
        return 0;
    }

    // ─── MAPEAMENTO ──────────────────────────────────────────────────────

    private FaturaDTO toDTO(PagamentoMensalidadeMain p, String restauranteNome) {
        String statusDTO = switch (p.getStatus()) {
            case PENDENTE  -> "PENDENTE";
            case PAGO      -> "PAGA";
            case REJEITADO -> "REJEITADA";
            case CANCELADO -> "CANCELADA";
        };
        Fatura.MetodoPagamento metodo = parseMetodo(p.getMetodoPagamento());
        String competencia = p.getCriadoEm() != null ? p.getCriadoEm().format(COMPETENCIA_FMT) : null;

        return FaturaDTO.builder()
                .id(p.getId())
                .assinaturaId(null)
                .restauranteId(p.getRestauranteId())
                .restauranteNome(restauranteNome)
                .planoNome(null) // pagamentos_mensalidade não tem plano direto — frontend mostra "—"
                .competencia(competencia)
                .valor(p.getValor() != null ? p.getValor().setScale(2, RoundingMode.HALF_UP) : null)
                .vencimentoEm(p.getCriadoEm() != null ? p.getCriadoEm().toLocalDate() : null)
                .status(statusDTO)
                .pagamentoEm(p.getPagoEm())
                .metodoPagamento(metodo != null ? metodo.name() : null)
                .externalPaymentId(p.getReferenciaGateway())
                .observacao(null)
                .criadoEm(p.getCriadoEm())
                .atualizadoEm(p.getPagoEm() != null ? p.getPagoEm() : p.getCriadoEm())
                .build();
    }

    private static Fatura.MetodoPagamento parseMetodo(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Fatura.MetodoPagamento.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Fatura.MetodoPagamento.OUTRO;
        }
    }

    private static PagamentoMensalidadeMain.Status mapearStatusBuscaToMain(String s) {
        if (s == null || s.isBlank()) return null;
        return switch (s.toUpperCase()) {
            case "PENDENTE", "VENCIDA" -> PagamentoMensalidadeMain.Status.PENDENTE;
            case "PAGA", "PAGO"        -> PagamentoMensalidadeMain.Status.PAGO;
            case "CANCELADA", "CANCELADO" -> PagamentoMensalidadeMain.Status.CANCELADO;
            default -> null;
        };
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────

    private String nomeRestaurante(Long id) {
        if (id == null) return null;
        return restauranteRepo.findById(id).map(r -> r.getNome()).orElse(null);
    }

    private Map<Long, String> nomesRestaurantes(List<Long> ids) {
        List<Long> dist = ids.stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (dist.isEmpty()) return new HashMap<>();
        Map<Long, String> out = new HashMap<>();
        restauranteRepo.findAllById(dist).forEach(r -> out.put(r.getId(), r.getNome()));
        return out;
    }

    public record GeracaoResumo(String competencia, int assinaturasAtivas, int faturasCriadas, int faturasPuladas) {}
}
