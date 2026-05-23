package com.mydelivery.admin.modulos.faturamento;

import java.util.List;

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

import com.mydelivery.admin.modulos.faturamento.dto.PlanoCreateRequest;
import com.mydelivery.admin.modulos.faturamento.dto.PlanoDTO;
import com.mydelivery.admin.modulos.faturamento.dto.PlanoUpdateRequest;
import com.mydelivery.admin.modulos.faturamento.service.PlanoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * CRUD do catálogo de planos.
 *
 *  GET    /api/admin/planos[?apenasAtivos=true]
 *  GET    /api/admin/planos/{id}
 *  POST   /api/admin/planos
 *  PATCH  /api/admin/planos/{id}
 *
 * ADMIN e FINANCEIRO podem mexer.
 */
@RestController
@RequestMapping("/api/admin/planos")
@PreAuthorize("hasAnyRole('ADMIN', 'FINANCEIRO')")
@RequiredArgsConstructor
public class PlanosController {

    private final PlanoService service;

    @GetMapping
    public ResponseEntity<List<PlanoDTO>> listar(
            @RequestParam(required = false) Boolean apenasAtivos) {
        return ResponseEntity.ok(service.listar(apenasAtivos));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanoDTO> detalhe(@PathVariable Long id) {
        return ResponseEntity.ok(service.detalhe(id));
    }

    @PostMapping
    public ResponseEntity<PlanoDTO> criar(@Valid @RequestBody PlanoCreateRequest req) {
        return ResponseEntity.ok(service.criar(req));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PlanoDTO> atualizar(@PathVariable Long id,
                                              @Valid @RequestBody PlanoUpdateRequest req) {
        return ResponseEntity.ok(service.atualizar(id, req));
    }
}
