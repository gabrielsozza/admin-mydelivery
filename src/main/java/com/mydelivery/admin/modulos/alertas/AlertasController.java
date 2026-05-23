package com.mydelivery.admin.modulos.alertas;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mydelivery.admin.modulos.alertas.dto.AlertaDetalheDTO;
import com.mydelivery.admin.modulos.alertas.dto.AlertaListDTO;
import com.mydelivery.admin.modulos.alertas.dto.AlertaUpdateRequest;
import com.mydelivery.admin.modulos.alertas.service.AlertaService;

import lombok.RequiredArgsConstructor;

/**
 * Endpoints de alertas.
 *
 *  GET    /api/admin/alertas              — paginado (filtros: status, severidade, tipo, restauranteId)
 *  GET    /api/admin/alertas/{id}         — detalhe
 *  PATCH  /api/admin/alertas/{id}         — reconhecer / resolver / ignorar
 *
 * ADMIN e SUPORTE podem mexer. Criação é só pelo monitor (sistema), não exposta como POST.
 */
@RestController
@RequestMapping("/api/admin/alertas")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPORTE')")
@RequiredArgsConstructor
public class AlertasController {

    private final AlertaService service;

    @GetMapping
    public ResponseEntity<Page<AlertaListDTO>> listar(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severidade,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Long restauranteId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(service.listar(status, severidade, tipo, restauranteId, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertaDetalheDTO> detalhe(@PathVariable Long id) {
        return ResponseEntity.ok(service.detalhe(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<AlertaDetalheDTO> atualizar(
            @PathVariable Long id,
            @RequestBody AlertaUpdateRequest req) {
        return ResponseEntity.ok(service.atualizar(id, req));
    }
}
