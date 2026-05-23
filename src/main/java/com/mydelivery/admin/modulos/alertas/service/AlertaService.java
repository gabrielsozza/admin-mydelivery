package com.mydelivery.admin.modulos.alertas.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mydelivery.admin.modulos.alertas.dto.AlertaDetalheDTO;
import com.mydelivery.admin.modulos.alertas.dto.AlertaListDTO;
import com.mydelivery.admin.modulos.alertas.dto.AlertaUpdateRequest;
import com.mydelivery.admin.modulos.alertas.entity.Alerta;
import com.mydelivery.admin.modulos.alertas.repository.AlertaRepository;
import com.mydelivery.admin.modulos.auth.entity.AdminUser;
import com.mydelivery.admin.modulos.auth.repository.AdminUserRepository;
import com.mydelivery.admin.shared.exception.NotFoundException;
import com.mydelivery.admin.shared.main.entity.RestauranteMain;
import com.mydelivery.admin.shared.main.repository.RestauranteMainRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertaService {

    private final AlertaRepository alertaRepo;
    private final AdminUserRepository adminRepo;
    private final RestauranteMainRepository restauranteRepo;

    // ─── EMITIR (chamado pelo monitor ou por outros services) ──────────────

    /**
     * Cria um alerta novo ou, se já existir um ATIVO com o mesmo dedupKey,
     * incrementa {@code ocorrencias} e atualiza {@code ultimaOcorrenciaEm}.
     */
    @Transactional
    public Alerta emitir(Alerta.Tipo tipo,
                         Alerta.Severidade severidade,
                         Long restauranteId,
                         String titulo,
                         String descricao,
                         String dadosJson,
                         String dedupContexto) {

        String dedupKey = tipo.name() + ":" + restauranteId
                + (dedupContexto == null || dedupContexto.isBlank() ? "" : ":" + dedupContexto);

        return alertaRepo.findFirstByDedupKeyAndStatus(dedupKey, Alerta.Status.ATIVO)
                .map(existente -> {
                    existente.setOcorrencias(existente.getOcorrencias() + 1);
                    existente.setUltimaOcorrenciaEm(LocalDateTime.now());
                    // se severidade aumentou, sobe
                    if (severidade.ordinal() > existente.getSeveridade().ordinal()) {
                        existente.setSeveridade(severidade);
                    }
                    // atualiza dados se vier novo
                    if (dadosJson != null) existente.setDados(dadosJson);
                    return alertaRepo.save(existente);
                })
                .orElseGet(() -> {
                    Alerta novo = Alerta.builder()
                            .tipo(tipo)
                            .severidade(severidade)
                            .restauranteId(restauranteId)
                            .titulo(titulo)
                            .descricao(descricao)
                            .dados(dadosJson)
                            .dedupKey(dedupKey)
                            .status(Alerta.Status.ATIVO)
                            .ocorrencias(1)
                            .build();
                    log.info("[Alerta] novo {} severidade={} restaurante={}", tipo, severidade, restauranteId);
                    return alertaRepo.save(novo);
                });
    }

    /**
     * Marca todos os alertas ATIVOS com esses dedupKeys como RESOLVIDOS
     * automaticamente. Chamado pelo monitor quando o problema sumiu.
     */
    @Transactional
    public int resolverAutomaticamente(List<String> dedupKeysAindaPresentes,
                                       Alerta.Tipo tipoEscopo) {
        // Pega todos ATIVOS do tipo monitorado que NÃO estão na lista atual
        List<Alerta> ativos = alertaRepo.findByStatus(Alerta.Status.ATIVO).stream()
                .filter(a -> a.getTipo() == tipoEscopo)
                .filter(a -> !dedupKeysAindaPresentes.contains(a.getDedupKey()))
                .toList();
        if (ativos.isEmpty()) return 0;

        LocalDateTime now = LocalDateTime.now();
        for (Alerta a : ativos) {
            a.setStatus(Alerta.Status.RESOLVIDO);
            a.setResolvidoEm(now);
            a.setResolvidoPor(null); // sistema
            a.setObservacao(appendNota(a.getObservacao(),
                    "[auto] problema deixou de ocorrer em " + now));
        }
        alertaRepo.saveAll(ativos);
        log.info("[Alerta] auto-resolvidos: {} (tipo={})", ativos.size(), tipoEscopo);
        return ativos.size();
    }

    // ─── LISTAR ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AlertaListDTO> listar(String statusStr,
                                      String severidadeStr,
                                      String tipoStr,
                                      Long restauranteId,
                                      int page, int size) {

        Alerta.Status status = parseEnum(Alerta.Status.class, statusStr);
        Alerta.Severidade sev = parseEnum(Alerta.Severidade.class, severidadeStr);
        Alerta.Tipo tipo = parseEnum(Alerta.Tipo.class, tipoStr);

        if (size <= 0 || size > 100) size = 25;
        if (page < 0) page = 0;
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "ultimaOcorrenciaEm"));

        Page<Alerta> alertas = alertaRepo.buscar(status, sev, tipo, restauranteId, pageable);
        Map<Long, String> nomes = nomesRestaurantes(alertas.map(Alerta::getRestauranteId).toList());

        return alertas.map(a -> AlertaListDTO.from(a, nomes.get(a.getRestauranteId())));
    }

    @Transactional(readOnly = true)
    public AlertaDetalheDTO detalhe(Long id) {
        Alerta a = alertaRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Alerta não encontrado"));
        String restNome = nomeRestaurante(a.getRestauranteId());
        String reconhNome = a.getReconhecidoPor() == null ? null : nomeAdmin(a.getReconhecidoPor());
        String resolvNome = a.getResolvidoPor() == null ? null : nomeAdmin(a.getResolvidoPor());
        return AlertaDetalheDTO.from(a, restNome, reconhNome, resolvNome);
    }

    // ─── ATUALIZAR (admin reconhece, resolve ou ignora manualmente) ────────

    @Transactional
    public AlertaDetalheDTO atualizar(Long id, AlertaUpdateRequest req) {
        Alerta a = alertaRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Alerta não encontrado"));

        Alerta.Status novoStatus = parseEnum(Alerta.Status.class, req.getStatus());
        if (novoStatus == null) {
            throw new IllegalArgumentException("Status inválido: " + req.getStatus());
        }
        if (novoStatus == Alerta.Status.ATIVO) {
            throw new IllegalArgumentException("Não dá pra reabrir manualmente — só sistema cria ATIVO");
        }

        AdminUser admin = currentAdminOrThrow();
        LocalDateTime now = LocalDateTime.now();

        switch (novoStatus) {
            case RECONHECIDO -> {
                a.setStatus(Alerta.Status.RECONHECIDO);
                a.setReconhecidoEm(now);
                a.setReconhecidoPor(admin.getId());
            }
            case RESOLVIDO -> {
                a.setStatus(Alerta.Status.RESOLVIDO);
                a.setResolvidoEm(now);
                a.setResolvidoPor(admin.getId());
                if (a.getReconhecidoEm() == null) {
                    a.setReconhecidoEm(now);
                    a.setReconhecidoPor(admin.getId());
                }
            }
            case IGNORADO -> {
                a.setStatus(Alerta.Status.IGNORADO);
                a.setResolvidoEm(now);
                a.setResolvidoPor(admin.getId());
            }
            default -> { /* não acontece */ }
        }

        if (req.getObservacao() != null && !req.getObservacao().isBlank()) {
            a.setObservacao(appendNota(a.getObservacao(),
                    "[" + admin.getEmail() + "] " + req.getObservacao().trim()));
        }
        alertaRepo.save(a);
        return detalhe(id);
    }

    // ─── KPIs ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public long countAtivos() { return alertaRepo.countAtivos(); }

    @Transactional(readOnly = true)
    public long countAtivosAltos() { return alertaRepo.countAtivosAltos(); }

    // ─── HELPERS ───────────────────────────────────────────────────────────

    private AdminUser currentAdminOrThrow() {
        var authn = SecurityContextHolder.getContext().getAuthentication();
        if (authn == null || authn.getName() == null) {
            throw new IllegalStateException("Admin não autenticado");
        }
        return adminRepo.findByEmailIgnoreCase(authn.getName())
                .orElseThrow(() -> new IllegalStateException("Admin do token não existe mais"));
    }

    private String nomeRestaurante(Long id) {
        if (id == null) return null;
        return restauranteRepo.findById(id).map(RestauranteMain::getNome).orElse(null);
    }

    private Map<Long, String> nomesRestaurantes(List<Long> ids) {
        List<Long> distintos = ids.stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (distintos.isEmpty()) return Map.of();
        Map<Long, String> out = new HashMap<>();
        restauranteRepo.findAllById(distintos).forEach(r -> out.put(r.getId(), r.getNome()));
        return out;
    }

    private String nomeAdmin(Long id) {
        if (id == null) return null;
        return adminRepo.findById(id)
                .map(a -> a.getNome() != null ? a.getNome() : a.getEmail())
                .orElse(null);
    }

    private static String appendNota(String existente, String linha) {
        if (existente == null || existente.isBlank()) return linha;
        return existente + "\n" + linha;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Enum.valueOf(type, s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
