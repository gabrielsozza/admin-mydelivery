package com.mydelivery.admin.modulos.alertas.dto;

import lombok.Data;

/** Atualização manual de um alerta (reconhecer, resolver, ignorar). */
@Data
public class AlertaUpdateRequest {
    /** RECONHECIDO, RESOLVIDO ou IGNORADO. ATIVO não é aceito aqui. */
    private String status;
    /** Texto livre que o admin deixa pra histórico. */
    private String observacao;
}
