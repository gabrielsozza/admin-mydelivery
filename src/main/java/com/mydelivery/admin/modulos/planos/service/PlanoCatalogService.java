package com.mydelivery.admin.modulos.planos.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mydelivery.admin.modulos.autocorrecao.service.MainDbWriter;
import com.mydelivery.admin.modulos.planos.dto.PlanoMainDTO;
import com.mydelivery.admin.modulos.planos.dto.PlanoUpsertRequest;
import com.mydelivery.admin.shared.exception.NotFoundException;
import com.mydelivery.admin.shared.main.entity.PlanoCatalogoMain;
import com.mydelivery.admin.shared.main.repository.PlanoCatalogoMainRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Catálogo de planos comerciais — agora REAL e EDITÁVEL.
 *
 * Lê de {@code planos_catalog} no main DB via mirror; escreve via MainDbWriter.
 * Substitui a versão anterior que rehardcodava os valores em Java.
 *
 * Endpoints expostos pelo controller: GET (lista + detalhe), POST (criar),
 * PUT (atualizar), DELETE (soft-delete, vira ativo=false).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanoCatalogService {

    private final PlanoCatalogoMainRepository repo;
    private final MainDbWriter mainWriter;

    @Transactional(readOnly = true, transactionManager = "mainTransactionManager")
    public List<PlanoMainDTO> listar() {
        List<PlanoCatalogoMain> todos = repo.findAllByOrderByOrdemAscIdAsc();
        BigDecimal valorMensalRef = todos.stream()
                .filter(p -> "MENSAL".equalsIgnoreCase(p.getCodigo()))
                .findFirst()
                .map(PlanoCatalogoMain::getValor)
                .orElse(null);
        return todos.stream().map(p -> toDTO(p, valorMensalRef)).toList();
    }

    @Transactional(readOnly = true, transactionManager = "mainTransactionManager")
    public Optional<PlanoMainDTO> porCodigo(String codigo) {
        if (codigo == null) return Optional.empty();
        return repo.findByCodigoIgnoreCase(codigo.trim()).map(p -> toDTO(p, null));
    }

    @Transactional(readOnly = true, transactionManager = "mainTransactionManager")
    public PlanoMainDTO porId(Long id) {
        return repo.findById(id)
                .map(p -> toDTO(p, null))
                .orElseThrow(() -> new NotFoundException("Plano " + id + " não encontrado"));
    }

    // ─── CRIAR ────────────────────────────────────────────────────────────

    public PlanoMainDTO criar(PlanoUpsertRequest req) {
        if (req.getCodigo() == null || req.getCodigo().isBlank())
            throw new IllegalArgumentException("Código é obrigatório.");
        if (req.getNome() == null || req.getNome().isBlank())
            throw new IllegalArgumentException("Nome é obrigatório.");
        if (req.getValor() == null || req.getValor().signum() < 0)
            throw new IllegalArgumentException("Valor inválido.");
        if (req.getDuracaoMeses() == null || req.getDuracaoMeses() <= 0)
            throw new IllegalArgumentException("Duração em meses deve ser >= 1.");
        // Garante código único
        repo.findByCodigoIgnoreCase(req.getCodigo().trim()).ifPresent(p -> {
            throw new IllegalArgumentException("Já existe plano com código '" + req.getCodigo() + "'.");
        });

        Long id = mainWriter.inserirPlanoCatalogo(
                req.getCodigo(), req.getNome(), req.getDescricao(),
                req.getValor(), req.getDuracaoMeses(),
                req.getRecomendado(), req.getAceitaCartao(), req.getAceitaPix(),
                req.getOnboardingTipo(), req.getFeaturesJson(),
                req.getAtivo(), req.getOrdem());

        return porId(id);
    }

    // ─── ATUALIZAR ────────────────────────────────────────────────────────

    public PlanoMainDTO atualizar(Long id, PlanoUpsertRequest req) {
        // confere que existe
        repo.findById(id).orElseThrow(() -> new NotFoundException("Plano " + id + " não encontrado"));
        if (req.getValor() != null && req.getValor().signum() < 0)
            throw new IllegalArgumentException("Valor inválido.");
        if (req.getDuracaoMeses() != null && req.getDuracaoMeses() <= 0)
            throw new IllegalArgumentException("Duração em meses deve ser >= 1.");

        int linhas = mainWriter.atualizarPlanoCatalogo(id,
                req.getNome(), req.getDescricao(),
                req.getValor(), req.getDuracaoMeses(),
                req.getRecomendado(), req.getAceitaCartao(), req.getAceitaPix(),
                req.getOnboardingTipo(), req.getFeaturesJson(),
                req.getAtivo(), req.getOrdem());
        if (linhas != 1) log.warn("[PlanoCatalog] atualizar id={} afetou {} linhas", id, linhas);
        return porId(id);
    }

    // ─── DELETE (soft) ─────────────────────────────────────────────────────

    public void desativar(Long id) {
        repo.findById(id).orElseThrow(() -> new NotFoundException("Plano " + id + " não encontrado"));
        mainWriter.desativarPlanoCatalogo(id);
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────

    private PlanoMainDTO toDTO(PlanoCatalogoMain p, BigDecimal valorMensalRef) {
        BigDecimal valorPorMes = BigDecimal.ZERO;
        if (p.getDuracaoMeses() != null && p.getDuracaoMeses() > 0 && p.getValor() != null) {
            valorPorMes = p.getValor().divide(new BigDecimal(p.getDuracaoMeses()),
                    2, RoundingMode.HALF_UP);
        }
        BigDecimal economia = BigDecimal.ZERO;
        int economiaPct = 0;
        // Se não passou referência (chamada por id/codigo), busca on-demand
        if (valorMensalRef == null) {
            valorMensalRef = repo.findByCodigoIgnoreCase("MENSAL")
                    .map(PlanoCatalogoMain::getValor).orElse(null);
        }
        if (valorMensalRef != null && p.getDuracaoMeses() != null && p.getDuracaoMeses() > 1
                && !"MENSAL".equalsIgnoreCase(p.getCodigo())) {
            BigDecimal ref = valorMensalRef.multiply(new BigDecimal(p.getDuracaoMeses()));
            economia = ref.subtract(p.getValor() == null ? BigDecimal.ZERO : p.getValor());
            if (economia.signum() < 0) economia = BigDecimal.ZERO;
            if (ref.signum() > 0 && economia.signum() > 0) {
                economiaPct = economia.multiply(new BigDecimal("100"))
                        .divide(ref, 0, RoundingMode.HALF_UP).intValue();
            }
        }
        return PlanoMainDTO.builder()
                .id(p.getId())
                .codigo(p.getCodigo())
                .nome(p.getNome())
                .descricao(p.getDescricao())
                .valor(p.getValor())
                .valorPorMes(valorPorMes)
                .duracaoMeses(p.getDuracaoMeses() == null ? 0 : p.getDuracaoMeses())
                .recomendado(Boolean.TRUE.equals(p.getRecomendado()))
                .aceitaCartao(Boolean.TRUE.equals(p.getAceitaCartao()))
                .aceitaPix(Boolean.TRUE.equals(p.getAceitaPix()))
                .onboardingTipo(p.getOnboardingTipo())
                .featuresJson(p.getFeaturesJson())
                .ativo(Boolean.TRUE.equals(p.getAtivo()))
                .ordem(p.getOrdem() == null ? 0 : p.getOrdem())
                .economiaTotal(economia)
                .economiaPercentual(economiaPct)
                .build();
    }
}
