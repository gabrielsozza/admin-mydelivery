package com.mydelivery.admin.modulos.configuracoes.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConfiguracaoUpdateRequest {

    /** Valor a salvar. Pode ser vazio pra limpar. */
    private String valor;

    /** Quando criar nova chave (fora das pré-definidas). */
    @NotBlank
    private String chave;

    private String descricao;
    private Boolean sensivel;
}
