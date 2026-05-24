package com.mydelivery.admin.modulos.restaurantes;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mydelivery.admin.modulos.restaurantes.dto.RestauranteDetalheDTO;
import com.mydelivery.admin.modulos.restaurantes.dto.RestauranteListDTO;
import com.mydelivery.admin.modulos.restaurantes.service.RestaurantesService;

import lombok.RequiredArgsConstructor;

/**
 * Endpoints de leitura/gestão dos restaurantes (admin).
 *
 *  GET /api/admin/restaurantes?status=ATIVO&q=pizza&page=0&size=25
 *  GET /api/admin/restaurantes/{id}
 *
 * Tudo aqui exige role ADMIN (granular pro futuro: SUPORTE pode ver lista mas
 * não pode mexer; FINANCEIRO foca em faturamento, etc — depois).
 */
@RestController
@RequestMapping("/api/admin/restaurantes")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class RestaurantesController {

    private final RestaurantesService service;

    @GetMapping
    public ResponseEntity<Page<RestauranteListDTO>> listar(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(service.listar(status, q, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RestauranteDetalheDTO> detalhe(@PathVariable Long id) {
        return ResponseEntity.ok(service.detalhe(id));
    }

    /**
     * Apaga DEFINITIVAMENTE o restaurante e tudo associado.
     * Operação irreversível — usar pra limpar cadastros lixo.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> apagar(@PathVariable Long id) {
        Map<String, Integer> detalhe = service.apagarDefinitivamente(id);
        int total = detalhe.values().stream().mapToInt(Integer::intValue).sum();
        return ResponseEntity.ok(Map.of(
            "ok", true,
            "restauranteId", id,
            "linhasRemovidas", total,
            "detalhe", detalhe,
            "mensagem", "Restaurante apagado definitivamente (" + total + " registros removidos)."
        ));
    }
}
