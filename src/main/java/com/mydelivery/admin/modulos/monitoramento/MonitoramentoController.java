package com.mydelivery.admin.modulos.monitoramento;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mydelivery.admin.modulos.monitoramento.dto.HealthStatusDTO;
import com.mydelivery.admin.modulos.monitoramento.entity.HealthSnapshot;
import com.mydelivery.admin.modulos.monitoramento.service.MonitoramentoService;
import com.mydelivery.admin.shared.exception.NotFoundException;
import com.mydelivery.admin.shared.main.entity.RestauranteMain;
import com.mydelivery.admin.shared.main.repository.RestauranteMainRepository;

import lombok.RequiredArgsConstructor;

/**
 * Endpoints de monitoramento.
 *
 *  POST   /api/admin/monitoramento/ciclo                    — dispara ciclo agora (debug)
 *  GET    /api/admin/monitoramento/restaurantes/{id}        — último snapshot
 *  GET    /api/admin/monitoramento/restaurantes/{id}/historico — últimos 50 snapshots
 */
@RestController
@RequestMapping("/api/admin/monitoramento")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPORTE')")
@RequiredArgsConstructor
public class MonitoramentoController {

    private final MonitoramentoService service;
    private final RestauranteMainRepository restauranteRepo;

    @PostMapping("/ciclo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MonitoramentoService.CicloResumo> rodarAgora() {
        return ResponseEntity.ok(service.rodarCiclo());
    }

    @GetMapping("/restaurantes/{id}")
    public ResponseEntity<HealthStatusDTO> status(@PathVariable Long id) {
        HealthSnapshot s = service.ultimoSnapshot(id);
        if (s == null) {
            throw new NotFoundException("Restaurante ainda não tem snapshot — espere o próximo ciclo");
        }
        String nome = restauranteRepo.findById(id).map(RestauranteMain::getNome).orElse(null);
        return ResponseEntity.ok(HealthStatusDTO.from(s, nome));
    }

    @GetMapping("/restaurantes/{id}/historico")
    public ResponseEntity<List<HealthStatusDTO>> historico(@PathVariable Long id) {
        String nome = restauranteRepo.findById(id).map(RestauranteMain::getNome).orElse(null);
        List<HealthStatusDTO> list = service.historico(id).stream()
                .map(s -> HealthStatusDTO.from(s, nome))
                .toList();
        return ResponseEntity.ok(list);
    }
}
