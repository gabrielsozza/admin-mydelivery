package com.mydelivery.admin.modulos.restaurantes.service;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.mydelivery.admin.modulos.autocorrecao.service.MainDbWriter;
import com.mydelivery.admin.modulos.restaurantes.dto.RestauranteDetalheDTO;
import com.mydelivery.admin.modulos.restaurantes.dto.RestauranteListDTO;
import com.mydelivery.admin.shared.exception.NotFoundException;
import com.mydelivery.admin.shared.main.entity.RestauranteMain;
import com.mydelivery.admin.shared.main.repository.RestauranteMainRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantesService {

    private final RestauranteMainRepository restauranteRepo;
    private final MainDbWriter writer;

    /**
     * Busca paginada com filtros.
     *
     * @param statusStr ATIVO/TRIAL/BLOQUEADO/CANCELADO (case-insensitive), null = todos
     * @param q         busca por nome, slug ou telefone (parcial)
     * @param page      0-based
     * @param size      máx 100
     */
    public Page<RestauranteListDTO> listar(String statusStr, String q, int page, int size) {
        RestauranteMain.Status status = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                status = RestauranteMain.Status.valueOf(statusStr.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // status inválido = ignora filtro (não quebra a busca)
            }
        }
        if (size <= 0 || size > 100) size = 25;
        if (page < 0) page = 0;
        Pageable pageable = PageRequest.of(page, size);

        return restauranteRepo.buscar(status, q, pageable).map(RestauranteListDTO::from);
    }

    public RestauranteDetalheDTO detalhe(Long id) {
        RestauranteMain r = restauranteRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Restaurante não encontrado"));
        return RestauranteDetalheDTO.from(r);
    }

    /**
     * Apaga o restaurante DEFINITIVAMENTE — incluindo todos os dados associados
     * (pedidos, produtos, cupons, mesas/QR codes, assinatura, suporte, usuário dono).
     * Operação irreversível. Usado pra limpar cadastros lixo de curiosos.
     *
     * @return map com contadores por tabela (auditoria)
     */
    public Map<String, Integer> apagarDefinitivamente(Long id) {
        // Valida que existe antes (mensagem clara em vez de erro silencioso)
        RestauranteMain r = restauranteRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Restaurante não encontrado"));
        log.warn("[Restaurante] APAGAR DEFINITIVO id={} nome={} status={}",
                id, r.getNome(), r.getStatus());
        return writer.apagarRestauranteCompletamente(id);
    }
}
