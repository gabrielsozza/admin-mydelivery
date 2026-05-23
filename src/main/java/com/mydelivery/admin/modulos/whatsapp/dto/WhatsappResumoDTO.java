package com.mydelivery.admin.modulos.whatsapp.dto;

import lombok.Builder;
import lombok.Data;

/** Resumo da saúde do bot WhatsApp na plataforma toda. */
@Data
@Builder
public class WhatsappResumoDTO {
    private long total;
    private long conectadas;
    private long aguardandoQr;
    private long desconectadas;
    private long erros;
    private long novas;
    private long botInativo;
}
