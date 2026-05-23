package com.mydelivery.admin.modulos.faturamento.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mydelivery.admin.modulos.faturamento.dto.KpiFinanceiroDTO;
import com.mydelivery.admin.shared.main.entity.AssinaturaMain;
import com.mydelivery.admin.shared.main.entity.PagamentoMensalidadeMain;
import com.mydelivery.admin.shared.main.repository.AssinaturaMainRepository;
import com.mydelivery.admin.shared.main.repository.PagamentoMensalidadeMainRepository;

import lombok.RequiredArgsConstructor;

/**
 * KPIs financeiros — lê do main DB.
 *
 * Importante: o conceito de "MRR" no main é diferente porque os planos não são
 * todos mensais (SEMESTRAL e ANUAL são pagamentos únicos). Aqui calculamos:
 *  - MRR estimado: soma(valor/duracao_meses) das ATIVAS — proxy aceitável
 *
 * Porém como Plano é enum (sem coluna duracao_meses no banco), precisaríamos
 * inferir do nome do plano. Pra evitar coupling, deixamos SUM(valor) e
 * convertemos no frontend ou no DTO se necessário.
 */
@Service
@RequiredArgsConstructor
public class FaturamentoKpiService {

    private final AssinaturaMainRepository assinaturaRepo;
    private final PagamentoMensalidadeMainRepository pagamentoRepo;

    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public KpiFinanceiroDTO calcular() {
        // "MRR" aproximado: usa soma direta do valor das ATIVAS. Não é MRR técnico
        // (planos anuais são pagamento único), mas é o melhor indicador sem coupling.
        BigDecimal somaValorAtivas = assinaturaRepo.somaMrr();
        BigDecimal mrr = nonNull(somaValorAtivas);
        BigDecimal arr = mrr.multiply(BigDecimal.valueOf(12));

        BigDecimal inadimplencia = pagamentoRepo.somaInadimplencia();

        LocalDate hoje = LocalDate.now();
        LocalDateTime inicioMes = hoje.withDayOfMonth(1).atStartOfDay();
        LocalDateTime inicioProxMes = hoje.withDayOfMonth(1).plusMonths(1).atStartOfDay();
        LocalDateTime inicioMesAnt = hoje.withDayOfMonth(1).minusMonths(1).atStartOfDay();

        BigDecimal recebidoMesAtual = pagamentoRepo.somaPagosEntre(inicioMes, inicioProxMes);
        BigDecimal recebidoMesAnt = pagamentoRepo.somaPagosEntre(inicioMesAnt, inicioMes);

        return KpiFinanceiroDTO.builder()
                .mrr(nonNull(mrr))
                .arr(nonNull(arr))
                .inadimplenciaTotal(nonNull(inadimplencia))
                .recebidoMesAtual(nonNull(recebidoMesAtual))
                .recebidoMesAnterior(nonNull(recebidoMesAnt))
                .assinaturasAtivas(assinaturaRepo.countByStatus(AssinaturaMain.Status.ATIVA))
                .assinaturasSuspensas(assinaturaRepo.countByStatus(AssinaturaMain.Status.INADIMPLENTE))
                .assinaturasCanceladas(assinaturaRepo.countByStatus(AssinaturaMain.Status.CANCELADA))
                .faturasPendentes(pagamentoRepo.countByStatus(PagamentoMensalidadeMain.Status.PENDENTE))
                .faturasVencidas(0L) // main não tem status VENCIDA explícito
                .faturasPagas(pagamentoRepo.countByStatus(PagamentoMensalidadeMain.Status.PAGO))
                .build();
    }

    private static BigDecimal nonNull(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
