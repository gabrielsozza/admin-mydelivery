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
    /** TODAS as desconectadas (manual + queda). Mantido pra compat. */
    private long desconectadas;
    /** Apenas as desconectadas por queda INESPERADA (problema real). */
    private long desconectadasInesperadas;
    /** Apenas as desconectadas pelo próprio dono (fluxo normal). */
    private long desconectadasManual;
    private long erros;
    private long novas;
    private long botInativo;
    /** Total de instâncias com problema real (inesperadas + erros).
     *  É o número que importa pro alerta. */
    private long comProblema;
}
