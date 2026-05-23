package com.mydelivery.admin.modulos.planos.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

/**
 * DTO de plano lido da tabela {@code planos_catalog} do main DB.
 *
 * Esta versão é editável (admin tem POST/PUT/DELETE). Mantém os campos antigos
 * (codigo, nome, valor, etc) que o frontend admin já consome + novos campos
 * pra suportar CRUD (id, descricao, featuresJson, ativo, ordem).
 */
@Data
@Builder
public class PlanoMainDTO {
    private Long id;
    private String codigo;          // MENSAL / SEMESTRAL / ANUAL / custom
    private String nome;
    private String descricao;
    private BigDecimal valor;
    private BigDecimal valorPorMes;
    private int duracaoMeses;
    private boolean recomendado;
    private boolean aceitaPix;
    private boolean aceitaCartao;
    private String onboardingTipo;
    /** JSON array string — ex: ["Pedidos ilimitados","Suporte"]. Frontend faz parse. */
    private String featuresJson;
    private boolean ativo;
    private int ordem;
    private BigDecimal economiaTotal;
    private int economiaPercentual;
}
