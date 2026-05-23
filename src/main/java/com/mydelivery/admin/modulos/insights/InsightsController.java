package com.mydelivery.admin.modulos.insights;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mydelivery.admin.modulos.insights.dto.ChurnDTO;
import com.mydelivery.admin.modulos.insights.dto.ConversaoTrialDTO;
import com.mydelivery.admin.modulos.insights.dto.GmvDTO;
import com.mydelivery.admin.modulos.insights.dto.TopRestauranteDTO;
import com.mydelivery.admin.modulos.insights.dto.VisaoGeralDTO;
import com.mydelivery.admin.modulos.insights.service.InsightsService;

import lombok.RequiredArgsConstructor;

/**
 * Métricas agregadas / BI pro dashboard executivo.
 *
 *  GET /api/admin/insights/visao-geral                        — bloco "à primeira vista"
 *  GET /api/admin/insights/gmv?meses=6                        — GMV total + série mensal
 *  GET /api/admin/insights/top/gmv?dias=30&limite=10          — top restaurantes por GMV
 *  GET /api/admin/insights/top/mrr?limite=10                  — top restaurantes por MRR (mais caros)
 *  GET /api/admin/insights/churn?meses=6                      — série e total de churn
 *  GET /api/admin/insights/conversao-trial                    — taxa aproximada trial → ativo
 *
 * ADMIN e FINANCEIRO podem ver. SUPORTE não tem acesso (foco em tickets/alertas).
 */
@RestController
@RequestMapping("/api/admin/insights")
@PreAuthorize("hasAnyRole('ADMIN', 'FINANCEIRO')")
@RequiredArgsConstructor
public class InsightsController {

    private final InsightsService service;

    @GetMapping("/visao-geral")
    public ResponseEntity<VisaoGeralDTO> visaoGeral() {
        return ResponseEntity.ok(service.visaoGeral());
    }

    @GetMapping("/gmv")
    public ResponseEntity<GmvDTO> gmv(@RequestParam(defaultValue = "6") int meses) {
        return ResponseEntity.ok(service.gmv(meses));
    }

    @GetMapping("/top/gmv")
    public ResponseEntity<List<TopRestauranteDTO>> topGmv(
            @RequestParam(defaultValue = "30") int dias,
            @RequestParam(defaultValue = "10") int limite) {
        return ResponseEntity.ok(service.topPorGmv(dias, limite));
    }

    @GetMapping("/top/mrr")
    public ResponseEntity<List<TopRestauranteDTO>> topMrr(
            @RequestParam(defaultValue = "10") int limite) {
        return ResponseEntity.ok(service.topPorMrr(limite));
    }

    @GetMapping("/churn")
    public ResponseEntity<ChurnDTO> churn(@RequestParam(defaultValue = "6") int meses) {
        return ResponseEntity.ok(service.churn(meses));
    }

    @GetMapping("/conversao-trial")
    public ResponseEntity<ConversaoTrialDTO> conversaoTrial() {
        return ResponseEntity.ok(service.conversaoTrial());
    }
}
