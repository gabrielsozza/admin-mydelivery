package com.mydelivery.admin.modulos.pagamentos;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mydelivery.admin.modulos.pagamentos.dto.CobrancaBoletoDTO;
import com.mydelivery.admin.modulos.pagamentos.dto.CobrancaBoletoRequest;
import com.mydelivery.admin.modulos.pagamentos.dto.CobrancaPixDTO;
import com.mydelivery.admin.modulos.pagamentos.service.CobrancaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Endpoints pra gerar cobrança MP de uma fatura.
 *
 *  POST /api/admin/faturas/{id}/cobrar/pix      — gera PIX (sem body)
 *  POST /api/admin/faturas/{id}/cobrar/boleto   — gera boleto (precisa CPF+nome)
 *
 * ADMIN e FINANCEIRO podem gerar cobrança.
 */
@RestController
@RequestMapping("/api/admin/faturas")
@PreAuthorize("hasAnyRole('ADMIN', 'FINANCEIRO')")
@RequiredArgsConstructor
public class CobrancaController {

    private final CobrancaService service;

    @PostMapping("/{id}/cobrar/pix")
    public ResponseEntity<CobrancaPixDTO> cobrarPix(@PathVariable Long id) {
        return ResponseEntity.ok(service.criarPix(id));
    }

    @PostMapping("/{id}/cobrar/boleto")
    public ResponseEntity<CobrancaBoletoDTO> cobrarBoleto(
            @PathVariable Long id,
            @Valid @RequestBody CobrancaBoletoRequest req) {
        return ResponseEntity.ok(service.criarBoleto(id, req));
    }
}
