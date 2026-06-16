package com.mydelivery.admin.modulos.faturamento.service;

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
import com.mydelivery.admin.modulos.faturamento.dto.AssinaturaCancelRequest;
import com.mydelivery.admin.modulos.faturamento.dto.AssinaturaCreateRequest;
import com.mydelivery.admin.modulos.faturamento.dto.AssinaturaDTO;
import com.mydelivery.admin.modulos.faturamento.dto.AssinaturaUpdateRequest;
import com.mydelivery.admin.shared.exception.NotFoundException;
import com.mydelivery.admin.shared.main.entity.AssinaturaMain;
import com.mydelivery.admin.shared.main.repository.AssinaturaMainRepository;
import com.mydelivery.admin.shared.main.repository.RestauranteMainRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de assinaturas — agora lê DIRETO do {@code assinaturas} do main DB.
 *
 * Mudanças vs versão anterior:
 *  - Não usa mais a tabela {@code assinatura} do admin DB (ficou shadow/legacy)
 *  - Não cria assinaturas — isso acontece via checkout no painel do restaurante
 *  - Escritas (suspender/reativar/cancelar) vão pro main via {@link MainDbWriter}
 *
 * Mapeamento de status (main → API admin DTO):
 *  - ATIVA → ATIVA
 *  - INADIMPLENTE → SUSPENSA (terminologia do admin pra apresentar ao operador)
 *  - TRIAL → ATIVA (mantém visível como ativa pra suporte)
 *  - CANCELADA → CANCELADA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssinaturaService {

    private final AssinaturaMainRepository repo;
    private final RestauranteMainRepository restauranteRepo;
    private final MainDbWriter writer;

    // ─── LISTAGEM ────────────────────────────────────────────────────────

    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public Page<AssinaturaDTO> listar(String statusStr, Long planoId, Long restauranteId,
                                      int page, int size) {
        AssinaturaMain.Status mainStatus = mapearStatusBuscaToMain(statusStr);
        String planoFiltro = null; // planoId vem como Long no DTO antigo; ignora pra simplificar
        if (size <= 0 || size > 100) size = 25;
        if (page < 0) page = 0;
        Pageable pageable = PageRequest.of(page, size);

        Page<AssinaturaMain> assinaturas = repo.buscar(mainStatus, planoFiltro, restauranteId, pageable);
        Map<Long, String> nomes = nomesRestaurantes(assinaturas.map(AssinaturaMain::getRestauranteId).toList());
        return assinaturas.map(a -> toDTO(a, nomes.get(a.getRestauranteId())));
    }

    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public AssinaturaDTO detalhe(Long id) {
        AssinaturaMain a = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Assinatura não encontrada"));
        return toDTO(a, nomeRestaurante(a.getRestauranteId()));
    }

    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public AssinaturaDTO atual(Long restauranteId) {
        AssinaturaMain a = repo.findFirstByRestauranteIdOrderByIdDesc(restauranteId)
                .orElseThrow(() -> new NotFoundException("Restaurante não tem assinatura"));
        return toDTO(a, nomeRestaurante(restauranteId));
    }

    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public List<AssinaturaDTO> historicoRestaurante(Long restauranteId) {
        String nome = nomeRestaurante(restauranteId);
        return repo.findByRestauranteIdOrderByIdDesc(restauranteId).stream()
                .map(a -> toDTO(a, nome))
                .toList();
    }

    // ─── AÇÕES ─────────────────────────────────────────────────────────────

    /**
     * Criação não é mais suportada via admin — o restaurante contrata via
     * checkout do painel dele, e o main app cria a Assinatura nesse fluxo.
     */
    public AssinaturaDTO criar(AssinaturaCreateRequest req) {
        throw new IllegalStateException(
            "Assinaturas são criadas via checkout no painel do restaurante. "
            + "O admin gerencia as existentes (suspender / cancelar / reativar).");
    }

    /** Atualização parcial — também não suportado (valor e dia vencimento vêm do plano enum). */
    public AssinaturaDTO atualizar(Long id, AssinaturaUpdateRequest req) {
        throw new IllegalStateException(
            "Valor e vencimento são derivados do plano contratado. "
            + "Não é possível alterar pelo admin.");
    }

    public AssinaturaDTO suspender(Long id, String motivo) {
        // Resolve restaurante a partir do id da assinatura
        AssinaturaMain a = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Assinatura não encontrada"));

        if ("CANCELADA".equals(a.getStatus() != null ? a.getStatus().name() : null)) {
            throw new IllegalStateException("Assinatura já está CANCELADA — não pode suspender");
        }

        // Suspende o restaurante (bloqueia + marca assinatura INADIMPLENTE)
        // Aceita TRIAL/ATIVA/PENDENTE — só não opera em CANCELADA
        int n = writer.suspenderRestauranteManual(a.getRestauranteId(),
                motivo != null && !motivo.isBlank() ? motivo : "Suspenso pelo admin");

        if (n == 0) {
            // Já estava bloqueado E já inadimplente — não precisa fazer nada
            log.info("[Assinatura] suspender id={} idempotente (já estava suspenso)", id);
        } else {
            log.info("[Assinatura] suspensa id={} restauranteId={} updates={} motivo={}",
                    id, a.getRestauranteId(), n, motivo);
        }
        return detalhe(id);
    }

    public AssinaturaDTO reativar(Long id) {
        int n = writer.reativarAssinatura(id);
        if (n == 0) throw new IllegalStateException("Assinatura não está INADIMPLENTE ou já mudou de estado");
        return detalhe(id);
    }

    public AssinaturaDTO cancelar(Long id, AssinaturaCancelRequest req) {
        int n = writer.cancelarAssinatura(id);
        if (n == 0) throw new IllegalStateException("Assinatura já está cancelada");
        log.info("[Assinatura] cancelada id={} motivo={}", id, req != null ? req.getMotivo() : null);
        return detalhe(id);
    }

    // ─── MAPEAMENTO ──────────────────────────────────────────────────────

    private AssinaturaDTO toDTO(AssinaturaMain a, String restauranteNome) {
        // Status mapping pra alinhar com DTO antigo (ATIVA/SUSPENSA/CANCELADA/PENDENTE)
        String statusAdmin = switch (a.getStatus()) {
            case ATIVA, TRIAL -> "ATIVA";
            case PENDENTE     -> "PENDENTE";
            case INADIMPLENTE -> "SUSPENSA";
            case CANCELADA    -> "CANCELADA";
        };
        return AssinaturaDTO.builder()
                .id(a.getId())
                .restauranteId(a.getRestauranteId())
                .restauranteNome(restauranteNome)
                .planoId(null) // não tem mais id numérico — plano é enum
                .planoNome(planoNomeExibicao(a.getPlano()))
                .valorMensal(a.getValor())
                .diaVencimento(a.getProximaCobranca() != null ? a.getProximaCobranca().getDayOfMonth() : null)
                .status(statusAdmin)
                .inicioEm(a.getTrialInicio() != null ? a.getTrialInicio().toLocalDate() : null)
                .fimEm(a.getCanceladoEm() != null ? a.getCanceladoEm().toLocalDate() : null)
                .motivoCancelamento(null)
                .criadoEm(a.getTrialInicio())
                .atualizadoEm(null)
                .build();
    }

    /** Converte enum string do main em rótulo amigável. */
    private static String planoNomeExibicao(String enumStr) {
        if (enumStr == null) return "Trial";
        return switch (enumStr) {
            case "MENSAL"    -> "Mensal";
            case "SEMESTRAL" -> "Semestral";
            case "ANUAL"     -> "Anual";
            default          -> enumStr;
        };
    }

    /** Mapeia status da busca (DTO admin) pro enum do main. */
    private static AssinaturaMain.Status mapearStatusBuscaToMain(String s) {
        if (s == null || s.isBlank()) return null;
        return switch (s.toUpperCase()) {
            case "ATIVA"      -> AssinaturaMain.Status.ATIVA;
            case "SUSPENSA"   -> AssinaturaMain.Status.INADIMPLENTE;
            case "CANCELADA"  -> AssinaturaMain.Status.CANCELADA;
            case "TRIAL"      -> AssinaturaMain.Status.TRIAL;
            default           -> null;
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
}
