package com.mydelivery.admin.modulos.tickets.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Payload pra adicionar mensagem no thread de um ticket. */
@Data
public class MensagemRequest {

    @NotBlank
    private String mensagem;

    /** URLs Cloudinary (já subidas pelo frontend). */
    private List<String> anexos;
}
