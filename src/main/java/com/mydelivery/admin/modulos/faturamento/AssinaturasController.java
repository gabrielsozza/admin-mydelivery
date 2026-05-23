package com.mydelivery.admin.modulos.faturamento;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mydelivery.admin.modulos.faturamento.dto.AssinaturaCancelRequest;
import com.mydelivery.admin.modulos.faturamento.dto.AssinaturaCreateRequest;
import com.mydelivery.admin.modulos.faturamento.dto.AssinaturaDTO;
import com.mydelivery.admin.modulos.faturamento.dto.AssinaturaUpdateRequest;
import com.mydelivery.admin.modulos.faturamento.service.AssinaturaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Endpoints de assinaturas.
 *
 *  GET    /api/admin/assinaturas?status=ATIVA&restauranteId=12
 *  GET    /api/admin/assinaturas/{id}
 *  GET    /api/admin/assinaturas/restaurante/{restauranteId}/atual
 *  GET    /api/admin/assinaturas/restaurante/{restauranteId}/historico
 *  POST   /api/admin/assinaturas
 *  PATCH  /api/admin/assinaturas/{id}                  — só valorMensal e diaVencimento
 *  POST   /api/admin/assinaturas/{id}/suspender
 *  POST   /api/admin/assinaturas/{id}/reativar
 *  POST   /api/admin/assinaturas/{id}/cancelar
 */
@RestController
@RequestMapping("/api/admin/assinaturas")
@PreAuthorize("hasAnyRole('ADMIN', 'FINANCEIRO')")
@RequiredArgsConstructor
public class AssinaturasController {

    private final AssinaturaService service;

    @GetMapping
    public ResponseEntity<Page<AssinaturaDTO>> listar(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long planoId,
            @RequestParam(required = false) Long restauranteId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(service.listar(status, planoId, restauranteId, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssinaturaDTO> detalhe(@PathVariable Long id) {
        return ResponseEntity.ok(service.detalhe(id));
    }

    @GetMapping("/restaurante/{restauranteId}/atual")
    public ResponseEntity<AssinaturaDTO> atual(@PathVariable Long restauranteId) {
        return ResponseEntity.ok(service.atual(restauranteId));
    }

    @GetMapping("/restaurante/{restauranteId}/historico")
    public ResponseEntity<List<AssinaturaDTO>> historico(@PathVariable Long restauranteId) {
        return ResponseEntity.ok(service.historicoRestaurante(restauranteId));
    }

    @PostMapping
    public ResponseEntity<AssinaturaDTO> criar(@Valid @RequestBody AssinaturaCreateRequest req) {
        return ResponseEntity.ok(service.criar(req));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<AssinaturaDTO> atualizar(@PathVariable Long id,
                                                   @Valid @RequestBody AssinaturaUpdateRequest req) {
        return ResponseEntity.ok(service.atualizar(id, req));
    }

    @PostMapping("/{id}/suspender")
    public ResponseEntity<AssinaturaDTO> suspender(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String motivo = body == null ? null : body.get("motivo");
        return ResponseEntity.ok(service.suspender(id, motivo));
    }

    @PostMapping("/{id}/reativar")
    public ResponseEntity<AssinaturaDTO> reativar(@PathVariable Long id) {
        return ResponseEntity.ok(service.reativar(id));
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AssinaturaDTO> cancelar(
            @PathVariable Long id,
            @RequestBody(required = false) AssinaturaCancelRequest req) {
        return ResponseEntity.ok(service.cancelar(id, req));
    }
}
