package com.mydelivery.admin.modulos.whatsapp;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mydelivery.admin.modulos.whatsapp.dto.WhatsappInstanceDetalheDTO;
import com.mydelivery.admin.modulos.whatsapp.dto.WhatsappInstanceListDTO;
import com.mydelivery.admin.modulos.whatsapp.dto.WhatsappResumoDTO;
import com.mydelivery.admin.modulos.whatsapp.service.WhatsappAdminService;

import lombok.RequiredArgsConstructor;

/**
 * Painel WhatsApp — monitoramento das instâncias Evolution dos restaurantes.
 *
 *  GET /api/admin/whatsapp                    — lista paginada (filtro status)
 *  GET /api/admin/whatsapp/{id}               — detalhe (inclui QR code se pendente)
 *  GET /api/admin/whatsapp/restaurante/{id}   — instância de um restaurante específico
 *  GET /api/admin/whatsapp/problematicas      — DESCONECTADA + ERRO (até 50)
 *  GET /api/admin/whatsapp/resumo             — contagens por status
 */
@RestController
@RequestMapping("/api/admin/whatsapp")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPORTE')")
@RequiredArgsConstructor
public class WhatsappController {

    private final WhatsappAdminService service;

    @GetMapping
    public ResponseEntity<Page<WhatsappInstanceListDTO>> listar(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(service.listar(status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WhatsappInstanceDetalheDTO> detalhe(@PathVariable Long id) {
        return ResponseEntity.ok(service.detalhe(id));
    }

    @GetMapping("/restaurante/{restauranteId}")
    public ResponseEntity<WhatsappInstanceDetalheDTO> doRestaurante(@PathVariable Long restauranteId) {
        return ResponseEntity.ok(service.doRestaurante(restauranteId));
    }

    @GetMapping("/problematicas")
    public ResponseEntity<List<WhatsappInstanceListDTO>> problematicas(
            @RequestParam(defaultValue = "50") int limite) {
        return ResponseEntity.ok(service.problematicas(limite));
    }

    @GetMapping("/resumo")
    public ResponseEntity<WhatsappResumoDTO> resumo() {
        return ResponseEntity.ok(service.resumo());
    }

    /**
     * Reinicia a sessão WhatsApp do restaurante (sem QR novo). Usado pra
     * "destravar" bot que dormiu. Operação leve — só refresca o Baileys.
     */
    @PostMapping("/{id}/restart")
    public ResponseEntity<Map<String, Object>> restart(@PathVariable Long id) {
        return ResponseEntity.ok(service.restart(id));
    }

    /**
     * Health-check real-time direto na Evolution (não usa cache do banco).
     * Devolve stateReal + coerente=false se banco e Evolution discordam —
     * sinal de "bot dormindo" ou status zumbi.
     */
    @GetMapping("/{id}/health-check")
    public ResponseEntity<Map<String, Object>> healthCheck(@PathVariable Long id) {
        return ResponseEntity.ok(service.healthCheck(id));
    }

    /**
     * Saúde REAL do bot — combina status enum + heartbeat de mensagens recebidas.
     * Devolve estado: OPERACIONAL / INSTAVEL / OFFLINE.
     * Inclui contador de minutos sem mensagem pra debug.
     */
    @GetMapping("/{id}/saude")
    public ResponseEntity<Map<String, Object>> saude(@PathVariable Long id) {
        return ResponseEntity.ok(service.saude(id));
    }

    /**
     * Histórico das últimas 24h pra gráfico de acompanhamento.
     * Cada item: { em, estado, minutosSemMensagem, reconexaoDisparada }.
     */
    @GetMapping("/{id}/historico-saude")
    public ResponseEntity<List<Map<String, Object>>> historicoSaude(@PathVariable Long id) {
        return ResponseEntity.ok(service.historicoSaude(id));
    }

    /**
     * RESET COMPLETO — destrava número shadow-banned. Apaga sessão na Evolution
     * e o restaurante precisa escanear NOVO QR. Use quando restart já tentou
     * várias vezes e o bot continua mudo.
     */
    @PostMapping("/{id}/reset-full")
    public ResponseEntity<Map<String, Object>> resetFull(@PathVariable Long id) {
        return ResponseEntity.ok(service.resetFull(id));
    }

    /** Últimos webhooks recebidos da Evolution — distingue Evolution muda
     *  vs WhatsApp silenciando mensagens vs erro de processamento. */
    @GetMapping("/{id}/eventos")
    public ResponseEntity<Map<String, Object>> eventos(@PathVariable Long id) {
        return ResponseEntity.ok(service.eventos(id));
    }
}
