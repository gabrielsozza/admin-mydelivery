package com.mydelivery.admin.modulos.faturamento.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mydelivery.admin.modulos.faturamento.dto.PlanoCreateRequest;
import com.mydelivery.admin.modulos.faturamento.dto.PlanoDTO;
import com.mydelivery.admin.modulos.faturamento.dto.PlanoUpdateRequest;
import com.mydelivery.admin.modulos.faturamento.entity.Plano;
import com.mydelivery.admin.modulos.faturamento.repository.PlanoRepository;
import com.mydelivery.admin.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlanoService {

    private final PlanoRepository repo;

    @Transactional(readOnly = true)
    public List<PlanoDTO> listar(Boolean apenasAtivos) {
        List<Plano> planos = apenasAtivos != null && apenasAtivos
                ? repo.findByAtivoOrderByValorMensalAsc(true)
                : repo.findAll();
        return planos.stream().map(PlanoDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public PlanoDTO detalhe(Long id) {
        return PlanoDTO.from(buscar(id));
    }

    @Transactional
    public PlanoDTO criar(PlanoCreateRequest req) {
        String codigo = req.getCodigo().trim().toLowerCase();
        if (repo.existsByCodigoIgnoreCase(codigo)) {
            throw new IllegalArgumentException("Já existe um plano com código '" + codigo + "'");
        }
        Plano p = Plano.builder()
                .codigo(codigo)
                .nome(req.getNome().trim())
                .descricao(req.getDescricao())
                .valorMensal(req.getValorMensal())
                .features(req.getFeatures())
                .ativo(req.getAtivo() != null ? req.getAtivo() : true)
                .build();
        return PlanoDTO.from(repo.save(p));
    }

    @Transactional
    public PlanoDTO atualizar(Long id, PlanoUpdateRequest req) {
        Plano p = buscar(id);
        if (req.getNome() != null && !req.getNome().isBlank()) p.setNome(req.getNome().trim());
        if (req.getDescricao() != null) p.setDescricao(req.getDescricao());
        if (req.getValorMensal() != null) p.setValorMensal(req.getValorMensal());
        if (req.getFeatures() != null) p.setFeatures(req.getFeatures());
        if (req.getAtivo() != null) p.setAtivo(req.getAtivo());
        return PlanoDTO.from(repo.save(p));
    }

    private Plano buscar(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Plano não encontrado"));
    }
}
