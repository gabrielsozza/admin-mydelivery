package com.mydelivery.admin.modulos.pagamentos.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Pra boleto MP exige CPF e nome do pagador (PIX é mais relaxado). */
@Data
public class CobrancaBoletoRequest {

    @NotBlank
    private String cpf;

    @NotBlank
    private String nome;

    /** Email do pagador (opcional — default usa o config). */
    private String email;
}
