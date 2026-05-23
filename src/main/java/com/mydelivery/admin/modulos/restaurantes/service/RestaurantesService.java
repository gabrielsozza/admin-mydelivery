package com.mydelivery.admin.modulos.restaurantes.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.mydelivery.admin.modulos.restaurantes.dto.RestauranteDetalheDTO;
import com.mydelivery.admin.modulos.restaurantes.dto.RestauranteListDTO;
import com.mydelivery.admin.shared.exception.NotFoundException;
import com.mydelivery.admin.shared.main.entity.RestauranteMain;
import com.mydelivery.admin.shared.main.repository.RestauranteMainRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RestaurantesService {

    private final RestauranteMainRepository restauranteRepo;

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
}
