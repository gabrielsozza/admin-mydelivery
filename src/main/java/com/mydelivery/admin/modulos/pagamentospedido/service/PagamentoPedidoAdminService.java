package com.mydelivery.admin.modulos.pagamentospedido.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mydelivery.admin.modulos.pagamentospedido.dto.PagamentoPedidoListDTO;
import com.mydelivery.admin.modulos.pagamentospedido.dto.PagamentoPedidoResumoDTO;
import com.mydelivery.admin.modulos.pagamentospedido.dto.PagamentoPedidoResumoDTO.MotivoFalha;
import com.mydelivery.admin.shared.main.entity.PagamentoPedidoMain;
import com.mydelivery.admin.shared.main.repository.PagamentoPedidoMainRepository;

import lombok.RequiredArgsConstructor;

/**
 * Visualização de pagamentos de pedidos (foco em falhas).
 *
 * Lê {@code pagamentos} do main DB. Apenas leitura — corrigir pagamento envolve
 * o cliente final, então não há ação direta do admin nesse nível.
 */
@Service
@RequiredArgsConstructor
public class PagamentoPedidoAdminService {

    private final PagamentoPedidoMainRepository repo;

    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public Page<PagamentoPedidoListDTO> listarFalhas(String statusStr, int diasJanela,
                                                     int page, int size) {
        PagamentoPedidoMain.Status status = parseEnum(PagamentoPedidoMain.Status.class, statusStr);
        if (status == PagamentoPedidoMain.Status.APROVADO) status = null; // anti-engano
        if (diasJanela < 1) diasJanela = 7;
        if (diasJanela > 90) diasJanela = 90;
        if (size <= 0 || size > 100) size = 25;
        if (page < 0) page = 0;

        LocalDateTime desde = LocalDateTime.now().minusDays(diasJanela);
        Pageable pageable = PageRequest.of(page, size);
        return repo.findFalhasDesde(status, desde, pageable).map(PagamentoPedidoListDTO::from);
    }

    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public PagamentoPedidoResumoDTO resumo(int diasJanela) {
        if (diasJanela < 1) diasJanela = 30;
        if (diasJanela > 90) diasJanela = 90;

        long aprovados   = repo.countByStatus(PagamentoPedidoMain.Status.APROVADO);
        long recusados   = repo.countByStatus(PagamentoPedidoMain.Status.RECUSADO);
        long expirados   = repo.countByStatus(PagamentoPedidoMain.Status.EXPIRADO);
        long cancelados  = repo.countByStatus(PagamentoPedidoMain.Status.CANCELADO);
        long pendentes   = repo.countByStatus(PagamentoPedidoMain.Status.PENDENTE);

        LocalDateTime desde = LocalDateTime.now().minusDays(diasJanela);
        Pageable top = PageRequest.of(0, 10);
        List<Object[]> rows = repo.topMotivosFalha(desde, top);
        List<MotivoFalha> motivos = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            String detail = (String) r[0];
            long ocorrencias = ((Number) r[1]).longValue();
            motivos.add(MotivoFalha.builder()
                    .detail(detail)
                    .amigavel(PagamentoPedidoListDTO.amigavel(detail, null))
                    .ocorrencias(ocorrencias)
                    .build());
        }

        return PagamentoPedidoResumoDTO.builder()
                .aprovados(aprovados)
                .recusados(recusados)
                .expirados(expirados)
                .cancelados(cancelados)
                .pendentes(pendentes)
                .topMotivos(motivos)
                .build();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String s) {
        if (s == null || s.isBlank()) return null;
        try { return Enum.valueOf(type, s.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
