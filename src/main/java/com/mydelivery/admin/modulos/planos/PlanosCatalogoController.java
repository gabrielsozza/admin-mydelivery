package com.mydelivery.admin.modulos.planos;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mydelivery.admin.modulos.planos.dto.PlanoMainDTO;
import com.mydelivery.admin.modulos.planos.dto.PlanoUpsertRequest;
import com.mydelivery.admin.modulos.planos.service.PlanoCatalogService;
import com.mydelivery.admin.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Catálogo de planos comerciais — agora EDITÁVEL.
 *
 *  GET    /api/admin/planos-main           — lista todos (inclui inativos pra admin ver)
 *  GET    /api/admin/planos-main/{codigo}  — detalhe por código (MENSAL/SEMESTRAL/ANUAL)
 *  POST   /api/admin/planos-main           — cria novo plano
 *  PUT    /api/admin/planos-main/{id}      — atualiza (campos null = mantém)
 *  DELETE /api/admin/planos-main/{id}      — soft delete (vira ativo=false)
 *
 * Alterações refletem AUTOMATICAMENTE no painel do restaurante (mesma tabela).
 */
@RestController
@RequestMapping("/api/admin/planos-main")
@PreAuthorize("hasAnyRole('ADMIN', 'FINANCEIRO', 'SUPORTE')")
@RequiredArgsConstructor
public class PlanosCatalogoController {

    private final PlanoCatalogService catalog;

    @GetMapping
    public ResponseEntity<List<PlanoMainDTO>> listar() {
        return ResponseEntity.ok(catalog.listar());
    }

    @GetMapping("/{codigo}")
    public ResponseEntity<PlanoMainDTO> detalhe(@PathVariable String codigo) {
        return ResponseEntity.ok(catalog.porCodigo(codigo)
                .orElseThrow(() -> new NotFoundException("Plano '" + codigo + "' não encontrado")));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCEIRO')")
    public ResponseEntity<PlanoMainDTO> criar(@RequestBody PlanoUpsertRequest req) {
        return ResponseEntity.ok(catalog.criar(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCEIRO')")
    public ResponseEntity<PlanoMainDTO> atualizar(@PathVariable Long id,
                                                  @RequestBody PlanoUpsertRequest req) {
        return ResponseEntity.ok(catalog.atualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCEIRO')")
    public ResponseEntity<Map<String, Object>> desativar(@PathVariable Long id) {
        catalog.desativar(id);
        return ResponseEntity.ok(Map.of("ok", true, "id", id, "ativo", false));
    }
}
