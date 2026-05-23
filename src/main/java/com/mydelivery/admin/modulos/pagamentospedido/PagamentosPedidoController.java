package com.mydelivery.admin.modulos.pagamentospedido;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mydelivery.admin.modulos.pagamentospedido.dto.PagamentoPedidoListDTO;
import com.mydelivery.admin.modulos.pagamentospedido.dto.PagamentoPedidoResumoDTO;
import com.mydelivery.admin.modulos.pagamentospedido.service.PagamentoPedidoAdminService;

import lombok.RequiredArgsConstructor;

/**
 * Monitoramento de pagamentos de PEDIDOS (cliente → restaurante).
 *
 *  GET /api/admin/pagamentos/falhas?status=RECUSADO&dias=7   — só falhas
 *  GET /api/admin/pagamentos/resumo?dias=30                  — contagens + top motivos
 */
@RestController
@RequestMapping("/api/admin/pagamentos")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPORTE', 'FINANCEIRO')")
@RequiredArgsConstructor
public class PagamentosPedidoController {

    private final PagamentoPedidoAdminService service;

    @GetMapping("/falhas")
    public ResponseEntity<Page<PagamentoPedidoListDTO>> falhas(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "7") int dias,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(service.listarFalhas(status, dias, page, size));
    }

    @GetMapping("/resumo")
    public ResponseEntity<PagamentoPedidoResumoDTO> resumo(
            @RequestParam(defaultValue = "30") int dias) {
        return ResponseEntity.ok(service.resumo(dias));
    }
}
