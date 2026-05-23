package com.mydelivery.admin.modulos.autocorrecao;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mydelivery.admin.modulos.autocorrecao.dto.RecoveryActionDetalheDTO;
import com.mydelivery.admin.modulos.autocorrecao.dto.RecoveryActionListDTO;
import com.mydelivery.admin.modulos.autocorrecao.service.RecoveryService;

import lombok.RequiredArgsConstructor;

/**
 * Endpoints da auto-correção.
 *
 *  GET    /api/admin/recovery                     — lista (filtros status/tipo/restauranteId)
 *  GET    /api/admin/recovery/{id}                — detalhe
 *  POST   /api/admin/recovery/{id}/retry          — força nova tentativa
 *  POST   /api/admin/recovery/{id}/cancelar       — cancela manualmente
 *  POST   /api/admin/recovery/lote                — dispara processarLote agora (debug)
 *
 * ADMIN tem tudo. SUPORTE pode ver e cancelar mas não disparar lote manual.
 */
@RestController
@RequestMapping("/api/admin/recovery")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPORTE')")
@RequiredArgsConstructor
public class RecoveryActionsController {

    private final RecoveryService service;

    @GetMapping
    public ResponseEntity<Page<RecoveryActionListDTO>> listar(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Long restauranteId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(service.listar(status, tipo, restauranteId, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecoveryActionDetalheDTO> detalhe(@PathVariable Long id) {
        return ResponseEntity.ok(service.detalhe(id));
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RecoveryActionDetalheDTO> forcarRetry(@PathVariable Long id) {
        return ResponseEntity.ok(service.forcarRetry(id));
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<RecoveryActionDetalheDTO> cancelar(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String motivo = body == null ? null : body.get("motivo");
        return ResponseEntity.ok(service.cancelar(id, motivo));
    }

    @PostMapping("/lote")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> rodarLote() {
        int n = service.processarLote();
        return ResponseEntity.ok(Map.of("processadas", n));
    }
}
