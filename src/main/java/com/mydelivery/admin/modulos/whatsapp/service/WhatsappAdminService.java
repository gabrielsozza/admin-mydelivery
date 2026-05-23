package com.mydelivery.admin.modulos.whatsapp.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mydelivery.admin.modulos.whatsapp.dto.WhatsappInstanceDetalheDTO;
import com.mydelivery.admin.modulos.whatsapp.dto.WhatsappInstanceListDTO;
import com.mydelivery.admin.modulos.whatsapp.dto.WhatsappResumoDTO;
import com.mydelivery.admin.shared.exception.NotFoundException;
import com.mydelivery.admin.shared.main.entity.WhatsappInstanceMain;
import com.mydelivery.admin.shared.main.repository.RestauranteMainRepository;
import com.mydelivery.admin.shared.main.repository.WhatsappInstanceMainRepository;

import lombok.RequiredArgsConstructor;

/**
 * Monitoramento das instâncias WhatsApp dos restaurantes (Evolution API).
 *
 * Lê {@code whatsapp_instances} do main DB. Não escreve — operações de reconexão
 * ficariam pra fase futura (precisariam chamar a Evolution API direto, o que é
 * responsabilidade do main app).
 */
@Service
@RequiredArgsConstructor
public class WhatsappAdminService {

    private final WhatsappInstanceMainRepository repo;
    private final RestauranteMainRepository restauranteRepo;

    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public WhatsappResumoDTO resumo() {
        return WhatsappResumoDTO.builder()
                .total(repo.count())
                .conectadas(repo.countByStatus(WhatsappInstanceMain.Status.CONECTADA))
                .aguardandoQr(repo.countByStatus(WhatsappInstanceMain.Status.AGUARDANDO_QR))
                .desconectadas(repo.countByStatus(WhatsappInstanceMain.Status.DESCONECTADA))
                .erros(repo.countByStatus(WhatsappInstanceMain.Status.ERRO))
                .novas(repo.countByStatus(WhatsappInstanceMain.Status.NOVA))
                .botInativo(0L) // contagem específica via query nova se virar prioridade
                .build();
    }

    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public Page<WhatsappInstanceListDTO> listar(String statusStr, int page, int size) {
        WhatsappInstanceMain.Status status = parseEnum(WhatsappInstanceMain.Status.class, statusStr);
        if (size <= 0 || size > 100) size = 25;
        if (page < 0) page = 0;
        Pageable pageable = PageRequest.of(page, size);

        Page<WhatsappInstanceMain> instancias = repo.buscar(status, pageable);
        Map<Long, String> nomes = nomesRestaurantes(instancias.map(WhatsappInstanceMain::getRestauranteId).toList());
        return instancias.map(w -> WhatsappInstanceListDTO.from(w, nomes.get(w.getRestauranteId())));
    }

    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public WhatsappInstanceDetalheDTO detalhe(Long id) {
        WhatsappInstanceMain w = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Instância WhatsApp não encontrada"));
        return WhatsappInstanceDetalheDTO.from(w, nomeRestaurante(w.getRestauranteId()));
    }

    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public WhatsappInstanceDetalheDTO doRestaurante(Long restauranteId) {
        WhatsappInstanceMain w = repo.findByRestauranteId(restauranteId)
                .orElseThrow(() -> new NotFoundException("Restaurante não tem instância WhatsApp"));
        return WhatsappInstanceDetalheDTO.from(w, nomeRestaurante(restauranteId));
    }

    @Transactional(transactionManager = "mainTransactionManager", readOnly = true)
    public List<WhatsappInstanceListDTO> problematicas(int limite) {
        if (limite < 1 || limite > 200) limite = 50;
        Pageable p = PageRequest.of(0, limite);
        List<WhatsappInstanceMain> rows = repo.findProblematicas(p);
        if (rows.isEmpty()) return List.of();
        Map<Long, String> nomes = nomesRestaurantes(rows.stream().map(WhatsappInstanceMain::getRestauranteId).toList());
        return rows.stream()
                .map(w -> WhatsappInstanceListDTO.from(w, nomes.get(w.getRestauranteId())))
                .toList();
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────

    private String nomeRestaurante(Long id) {
        if (id == null) return null;
        return restauranteRepo.findById(id).map(r -> r.getNome()).orElse(null);
    }

    private Map<Long, String> nomesRestaurantes(List<Long> ids) {
        List<Long> dist = ids.stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (dist.isEmpty()) return new HashMap<>();
        Map<Long, String> out = new HashMap<>();
        restauranteRepo.findAllById(dist).forEach(r -> out.put(r.getId(), r.getNome()));
        return out;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String s) {
        if (s == null || s.isBlank()) return null;
        try { return Enum.valueOf(type, s.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
