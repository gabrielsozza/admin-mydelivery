package com.mydelivery.admin.modulos.tickets;

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

import com.mydelivery.admin.modulos.tickets.dto.MensagemDTO;
import com.mydelivery.admin.modulos.tickets.dto.MensagemRequest;
import com.mydelivery.admin.modulos.tickets.dto.TicketCreateRequest;
import com.mydelivery.admin.modulos.tickets.dto.TicketDetalheDTO;
import com.mydelivery.admin.modulos.tickets.dto.TicketListDTO;
import com.mydelivery.admin.modulos.tickets.dto.TicketUpdateRequest;
import com.mydelivery.admin.modulos.tickets.service.TicketService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Endpoints de tickets de suporte.
 *
 *  GET    /api/admin/tickets                       — lista paginada com filtros
 *  GET    /api/admin/tickets/{id}                  — detalhe + thread
 *  POST   /api/admin/tickets                       — cria ticket
 *  PATCH  /api/admin/tickets/{id}                  — atualiza status/prioridade/categoria/atribuição
 *  POST   /api/admin/tickets/{id}/mensagens        — adiciona mensagem no thread
 *
 *  ADMIN e SUPORTE têm acesso. FINANCEIRO não.
 */
@RestController
@RequestMapping("/api/admin/tickets")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPORTE')")
@RequiredArgsConstructor
public class TicketsController {

    private final TicketService service;

    @GetMapping
    public ResponseEntity<Page<TicketListDTO>> listar(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String prioridade,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) Long atribuidoA,
            @RequestParam(required = false) Long restauranteId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(
                service.listar(status, prioridade, categoria, atribuidoA, restauranteId, q, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketDetalheDTO> detalhe(@PathVariable Long id) {
        return ResponseEntity.ok(service.detalhe(id));
    }

    @PostMapping
    public ResponseEntity<TicketDetalheDTO> criar(@Valid @RequestBody TicketCreateRequest req) {
        return ResponseEntity.ok(service.criar(req));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TicketDetalheDTO> atualizar(
            @PathVariable Long id,
            @RequestBody TicketUpdateRequest req) {
        return ResponseEntity.ok(service.atualizar(id, req));
    }

    @PostMapping("/{id}/mensagens")
    public ResponseEntity<MensagemDTO> adicionarMensagem(
            @PathVariable Long id,
            @Valid @RequestBody MensagemRequest req) {
        return ResponseEntity.ok(service.adicionarMensagem(id, req));
    }
}
