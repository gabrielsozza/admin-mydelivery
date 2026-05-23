package com.mydelivery.admin.modulos.configuracoes.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mydelivery.admin.modulos.configuracoes.entity.ConfiguracaoAdmin;

public interface ConfiguracaoAdminRepository extends JpaRepository<ConfiguracaoAdmin, Long> {

    Optional<ConfiguracaoAdmin> findByChave(String chave);

    List<ConfiguracaoAdmin> findAllByOrderByChaveAsc();
}
