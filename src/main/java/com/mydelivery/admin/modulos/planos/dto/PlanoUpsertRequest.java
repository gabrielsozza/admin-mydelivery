package com.mydelivery.admin.modulos.planos.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * Body comum pra criar (POST) e atualizar (PUT) plano. Em PUT, campos null
 * mantêm o valor atual. Em POST, campos obrigatórios validados no service.
 */
@Data
public class PlanoUpsertRequest {
    private String codigo;            // só usado no POST
    private String nome;
    private String descricao;
    private BigDecimal valor;
    private Integer duracaoMeses;
    private Boolean recomendado;
    private Boolean aceitaCartao;
    private Boolean aceitaPix;
    private String onboardingTipo;
    private String featuresJson;
    private Boolean ativo;
    private Integer ordem;
}
