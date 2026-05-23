package com.mydelivery.admin.modulos.insights.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mydelivery.admin.modulos.alertas.repository.AlertaRepository;
import com.mydelivery.admin.modulos.autocorrecao.repository.RecoveryActionRepository;
import com.mydelivery.admin.modulos.faturamento.dto.KpiFinanceiroDTO;
import com.mydelivery.admin.modulos.faturamento.entity.Assinatura;
import com.mydelivery.admin.modulos.faturamento.repository.AssinaturaRepository;
import com.mydelivery.admin.modulos.faturamento.service.FaturamentoKpiService;
import com.mydelivery.admin.modulos.insights.dto.ChurnDTO;
import com.mydelivery.admin.modulos.insights.dto.ConversaoTrialDTO;
import com.mydelivery.admin.modulos.insights.dto.GmvDTO;
import com.mydelivery.admin.modulos.insights.dto.SerieMensalDTO;
import com.mydelivery.admin.modulos.insights.dto.TopRestauranteDTO;
import com.mydelivery.admin.modulos.insights.dto.VisaoGeralDTO;
import com.mydelivery.admin.shared.main.entity.RestauranteMain;
import com.mydelivery.admin.shared.main.repository.PedidoMainRepository;
import com.mydelivery.admin.shared.main.repository.RestauranteMainRepository;
import com.mydelivery.admin.shared.main.repository.SuporteTicketMainRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Métricas agregadas pro dashboard executivo do admin.
 *
 * Cuidado:
 *  - Queries no PedidoMain podem ser pesadas (tabela do main pode ter milhões de linhas).
 *    Use sempre filtros de data e GROUP BY.
 *  - Várias métricas são aproximações por falta de histórico de transições.
 *    Documentado nos DTOs quando aplicável.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightsService {

    private final RestauranteMainRepository restauranteRepo;
    private final PedidoMainRepository pedidoRepo;
    private final AssinaturaRepository assinaturaRepo;
    private final FaturamentoKpiService kpiService;
    private final SuporteTicketMainRepository ticketRepo;
    private final AlertaRepository alertaRepo;
    private final RecoveryActionRepository recoveryRepo;

    // ─── VISÃO GERAL ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public VisaoGeralDTO visaoGeral() {
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime ha30dias = agora.minusDays(30);
        LocalDateTime ha60dias = agora.minusDays(60);

        KpiFinanceiroDTO kpis = kpiService.calcular();

        return VisaoGeralDTO.builder()
                .restaurantesAtivos(restauranteRepo.countByStatus(RestauranteMain.Status.ATIVO))
                .restaurantesTrial(restauranteRepo.countByStatus(RestauranteMain.Status.TRIAL))
                .restaurantesBloqueados(restauranteRepo.countByStatus(RestauranteMain.Status.BLOQUEADO))
                .restaurantesCancelados(restauranteRepo.countByStatus(RestauranteMain.Status.CANCELADO))
                .restaurantesNovosUltimos30Dias(restauranteRepo.countByCriadoEmBetween(ha30dias, agora))
                .mrr(kpis.getMrr())
                .arr(kpis.getArr())
                .recebidoMesAtual(kpis.getRecebidoMesAtual())
                .recebidoMesAnterior(kpis.getRecebidoMesAnterior())
                .inadimplenciaTotal(kpis.getInadimplenciaTotal())
                .gmvUltimos30Dias(safeGmv(ha30dias, agora))
                .gmvMesAnterior(safeGmv(ha60dias, ha30dias))
                .pedidosUltimos30Dias(safeCountPedidos(ha30dias, agora))
                .ticketsAbertos(ticketRepo.countAbertos())
                .alertasAtivos(alertaRepo.countAtivos())
                .alertasAtivosAltos(alertaRepo.countAtivosAltos())
                .recoveryEmAndamento(recoveryRepo.countEmAndamento())
                .build();
    }

    // ─── GMV ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public GmvDTO gmv(int meses) {
        if (meses < 1) meses = 6;
        if (meses > 36) meses = 36;
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime inicio = agora.minusMonths(meses).withDayOfMonth(1)
                .toLocalDate().atStartOfDay();

        BigDecimal total = safeGmv(inicio, agora);
        long pedidos = safeCountPedidos(inicio, agora);
        BigDecimal ticketMedio = pedidos > 0
                ? total.divide(BigDecimal.valueOf(pedidos), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return GmvDTO.builder()
                .totalPeriodo(total)
                .pedidosPeriodo(pedidos)
                .ticketMedio(ticketMedio)
                .serieMensal(safeSerieMensalPedidos(inicio))
                .build();
    }

    // ─── TOP RESTAURANTES ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TopRestauranteDTO> topPorGmv(int dias, int limite) {
        if (limite < 1 || limite > 100) limite = 10;
        if (dias < 1) dias = 30;
        LocalDateTime fim = LocalDateTime.now();
        LocalDateTime inicio = fim.minusDays(dias);

        List<Object[]> rows;
        try {
            rows = pedidoRepo.topRestaurantesPorGmv(inicio, fim, PageRequest.of(0, limite));
        } catch (Exception e) {
            log.warn("[Insights] topPorGmv falhou (tabela pedido não disponível?): {}", e.getMessage());
            return List.of();
        }
        if (rows.isEmpty()) return List.of();

        List<Long> ids = rows.stream().map(r -> ((Number) r[0]).longValue()).toList();
        Map<Long, String> nomes = nomesRestaurantes(ids);

        List<TopRestauranteDTO> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            Long id = ((Number) r[0]).longValue();
            out.add(TopRestauranteDTO.builder()
                    .restauranteId(id)
                    .restauranteNome(nomes.get(id))
                    .valor((BigDecimal) r[1])
                    .quantidade(((Number) r[2]).longValue())
                    .build());
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<TopRestauranteDTO> topPorMrr(int limite) {
        if (limite < 1 || limite > 100) limite = 10;
        List<Assinatura> ativas = assinaturaRepo.findByStatus(Assinatura.Status.ATIVA);
        // ordena por valorMensal desc e limita
        List<Assinatura> top = ativas.stream()
                .sorted((a, b) -> b.getValorMensal().compareTo(a.getValorMensal()))
                .limit(limite)
                .toList();

        Map<Long, String> nomes = nomesRestaurantes(
                top.stream().map(Assinatura::getRestauranteId).toList());

        return top.stream()
                .map(a -> TopRestauranteDTO.builder()
                        .restauranteId(a.getRestauranteId())
                        .restauranteNome(nomes.get(a.getRestauranteId()))
                        .valor(a.getValorMensal())
                        .quantidade(0)
                        .build())
                .toList();
    }

    // ─── CHURN ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ChurnDTO churn(int meses) {
        if (meses < 1) meses = 6;
        if (meses > 24) meses = 24;
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime inicio = agora.minusMonths(meses).withDayOfMonth(1)
                .toLocalDate().atStartOfDay();

        // série mensal: usa bloqueado_em como proxy
        List<SerieMensalDTO> serie = new ArrayList<>();
        long churnAbsoluto = 0;

        LocalDate cursor = inicio.toLocalDate();
        LocalDate fim = agora.toLocalDate();
        while (!cursor.isAfter(fim)) {
            LocalDateTime mIni = cursor.withDayOfMonth(1).atStartOfDay();
            LocalDateTime mFim = cursor.withDayOfMonth(1).plusMonths(1).atStartOfDay();
            long bloq = restauranteRepo.countByBloqueadoEmBetween(mIni, mFim);
            serie.add(SerieMensalDTO.builder()
                    .competencia(String.format("%04d-%02d", cursor.getYear(), cursor.getMonthValue()))
                    .valor(BigDecimal.valueOf(bloq))
                    .quantidade(bloq)
                    .build());
            churnAbsoluto += bloq;
            cursor = cursor.plusMonths(1);
        }

        long ativos = restauranteRepo.countByStatus(RestauranteMain.Status.ATIVO);
        double taxa = ativos == 0 ? 0.0
                : (churnAbsoluto * 100.0) / (ativos + churnAbsoluto);

        return ChurnDTO.builder()
                .periodoInicio(formatCompetencia(inicio))
                .periodoFim(formatCompetencia(agora))
                .churnAbsoluto(churnAbsoluto)
                .taxaPercentualAproximada(round2(taxa))
                .serieMensal(serie)
                .build();
    }

    // ─── CONVERSÃO TRIAL ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ConversaoTrialDTO conversaoTrial() {
        long ativos = restauranteRepo.countByStatus(RestauranteMain.Status.ATIVO);
        long trial = restauranteRepo.countByStatus(RestauranteMain.Status.TRIAL);
        long bloqueados = restauranteRepo.countByStatus(RestauranteMain.Status.BLOQUEADO);
        long cancelados = restauranteRepo.countByStatus(RestauranteMain.Status.CANCELADO);
        long total = ativos + trial + bloqueados + cancelados;
        double taxa = total == 0 ? 0.0 : ((total - trial) * 100.0) / total;

        return ConversaoTrialDTO.builder()
                .totalRestaurantes(total)
                .emTrialAgora(trial)
                .ativos(ativos)
                .bloqueados(bloqueados)
                .cancelados(cancelados)
                .taxaConversaoAproximada(round2(taxa))
                .build();
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────

    private BigDecimal safeGmv(LocalDateTime inicio, LocalDateTime fim) {
        try {
            BigDecimal v = pedidoRepo.somaGmvEntre(inicio, fim);
            return v == null ? BigDecimal.ZERO : v;
        } catch (Exception e) {
            log.warn("[Insights] somaGmvEntre falhou (tabela pedido n/d?): {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private long safeCountPedidos(LocalDateTime inicio, LocalDateTime fim) {
        try {
            return pedidoRepo.countPedidosEntre(inicio, fim);
        } catch (Exception e) {
            log.warn("[Insights] countPedidosEntre falhou (tabela pedido n/d?): {}", e.getMessage());
            return 0L;
        }
    }

    private List<SerieMensalDTO> safeSerieMensalPedidos(LocalDateTime inicio) {
        try {
            List<Object[]> rows = pedidoRepo.serieMensal(inicio);
            List<SerieMensalDTO> out = new ArrayList<>(rows.size());
            for (Object[] r : rows) {
                int ano = ((Number) r[0]).intValue();
                int mes = ((Number) r[1]).intValue();
                out.add(SerieMensalDTO.builder()
                        .competencia(String.format("%04d-%02d", ano, mes))
                        .valor((BigDecimal) r[2])
                        .quantidade(((Number) r[3]).longValue())
                        .build());
            }
            return out;
        } catch (Exception e) {
            log.warn("[Insights] serieMensal pedidos falhou: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<Long, String> nomesRestaurantes(List<Long> ids) {
        List<Long> dist = ids.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (dist.isEmpty()) return new HashMap<>();
        Map<Long, String> out = new HashMap<>();
        restauranteRepo.findAllById(dist).forEach(r -> out.put(r.getId(), r.getNome()));
        return out;
    }

    private static String formatCompetencia(LocalDateTime t) {
        return String.format("%04d-%02d", t.getYear(), t.getMonthValue());
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
