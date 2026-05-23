package com.mydelivery.admin.modulos.configuracoes;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mydelivery.admin.modulos.configuracoes.dto.ConfiguracaoDTO;
import com.mydelivery.admin.modulos.configuracoes.service.ConfiguracaoAdminService;

import lombok.RequiredArgsConstructor;

/**
 * Configurações dinâmicas do admin (MP credentials, ambiente, etc).
 *
 *  GET /api/admin/configuracoes            — lista todas (valores sensíveis vêm mascarados)
 *  GET /api/admin/configuracoes/{chave}    — detalhe (também mascarado se sensível)
 *  PUT /api/admin/configuracoes/{chave}    — salva valor { "valor": "..." }
 *
 * Apenas ADMIN — config global crítica.
 */
@RestController
@RequestMapping("/api/admin/configuracoes")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ConfiguracoesController {

    private final ConfiguracaoAdminService service;

    @GetMapping
    public ResponseEntity<List<ConfiguracaoDTO>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @GetMapping("/{chave}")
    public ResponseEntity<ConfiguracaoDTO> detalhe(@PathVariable String chave) {
        return ResponseEntity.ok(service.detalhe(chave));
    }

    @PutMapping("/{chave}")
    public ResponseEntity<ConfiguracaoDTO> salvar(
            @PathVariable String chave,
            @RequestBody Map<String, String> body) {
        String valor = body == null ? null : body.get("valor");
        return ResponseEntity.ok(service.salvar(chave, valor));
    }
}
