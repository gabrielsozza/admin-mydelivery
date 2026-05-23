package com.mydelivery.admin.shared.main.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Espelho READ-ONLY de {@code planos_catalog} do main DB.
 *
 * Tabela criada pra que o admin possa editar planos comerciais sem redeploy.
 * Lê via JPA aqui, escreve via {@link com.mydelivery.admin.modulos.autocorrecao.service.MainDbWriter}.
 */
@Entity
@Table(name = "planos_catalog")
@Data
@NoArgsConstructor
public class PlanoCatalogoMain {

    @Id
    private Long id;

    private String codigo;
    private String nome;
    private String descricao;
    private BigDecimal valor;

    @Column(name = "duracao_meses")
    private Integer duracaoMeses;

    private Boolean recomendado;

    @Column(name = "aceita_cartao")
    private Boolean aceitaCartao;

    @Column(name = "aceita_pix")
    private Boolean aceitaPix;

    @Column(name = "onboarding_tipo")
    private String onboardingTipo;

    @Column(name = "features_json", columnDefinition = "TEXT")
    private String featuresJson;

    private Boolean ativo;
    private Integer ordem;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;
}
