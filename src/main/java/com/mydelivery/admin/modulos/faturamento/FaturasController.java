package com.mydelivery.admin.modulos.faturamento;

import java.time.LocalDate;
import java.util.List;
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

import com.mydelivery.admin.modulos.faturamento.dto.FaturaDTO;
import com.mydelivery.admin.modulos.faturamento.dto.FaturaMarcarPagaRequest;
import com.mydelivery.admin.modulos.faturamento.dto.KpiFinanceiroDTO;
import com.mydelivery.admin.modulos.faturamento.service.FaturaService;
import com.mydelivery.admin.modulos.faturamento.service.FaturamentoKpiService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Endpoints das faturas + KPIs financeiros.
 *
 *  GET    /api/admin/faturas?status=VENCIDA&competencia=2026-05
 *  GET    /api/admin/faturas/{id}
 *  GET    /api/admin/faturas/restaurante/{restauranteId}
 *  POST   /api/admin/faturas/{id}/pagar
 *  POST   /api/admin/faturas/{id}/cancelar
 *  POST   /api/admin/faturas/gerar?competencia=YYYY-MM-DD (debug — força geração de competência)
 *  POST   /api/admin/faturas/marcar-vencidas (debug — roda job de vencidas agora)
 *
 *  GET    /api/admin/faturas/kpis
 */
@RestController
@RequestMapping("/api/admin/faturas")
@PreAuthorize("hasAnyRole('ADMIN', 'FINANCEIRO')")
@RequiredArgsConstructor
public class FaturasController {

    private final FaturaService faturaService;
    private final FaturamentoKpiService kpiService;

    @GetMapping
    public ResponseEntity<Page<FaturaDTO>> listar(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long restauranteId,
            @RequestParam(required = false) String competencia,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(faturaService.listar(status, restauranteId, competencia, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FaturaDTO> detalhe(@PathVariable Long id) {
        return ResponseEntity.ok(faturaService.detalhe(id));
    }

    @GetMapping("/restaurante/{restauranteId}")
    public ResponseEntity<List<FaturaDTO>> doRestaurante(@PathVariable Long restauranteId) {
        return ResponseEntity.ok(faturaService.doRestaurante(restauranteId));
    }

    @PostMapping("/{id}/pagar")
    public ResponseEntity<FaturaDTO> marcarPaga(
            @PathVariable Long id,
            @Valid @RequestBody FaturaMarcarPagaRequest req) {
        return ResponseEntity.ok(faturaService.marcarPaga(id, req));
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FaturaDTO> cancelar(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String motivo = body == null ? null : body.get("motivo");
        return ResponseEntity.ok(faturaService.cancelar(id, motivo));
    }

    @PostMapping("/gerar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FaturaService.GeracaoResumo> gerar(
            @RequestParam(required = false) String competencia) {
        LocalDate ref = (competencia == null || competencia.isBlank())
                ? LocalDate.now()
                : LocalDate.parse(competencia);
        return ResponseEntity.ok(faturaService.gerarFaturasDaCompetencia(ref));
    }

    @PostMapping("/marcar-vencidas")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Integer>> marcarVencidas() {
        return ResponseEntity.ok(Map.of("marcadas", faturaService.marcarVencidas()));
    }

    @GetMapping("/kpis")
    public ResponseEntity<KpiFinanceiroDTO> kpis() {
        return ResponseEntity.ok(kpiService.calcular());
    }
}
