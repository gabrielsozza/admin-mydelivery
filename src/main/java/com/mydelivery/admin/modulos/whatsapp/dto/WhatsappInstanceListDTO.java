package com.mydelivery.admin.modulos.whatsapp.dto;

import java.time.LocalDateTime;

import com.mydelivery.admin.shared.main.entity.WhatsappInstanceMain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WhatsappInstanceListDTO {
    private Long id;
    private Long restauranteId;
    private String restauranteNome;
    private String instanceName;
    private String phone;
    private String status;
    private Boolean botAtivo;
    private LocalDateTime conectadoEm;
    private LocalDateTime atualizadoEm;
    private Boolean qrPendente;

    public static WhatsappInstanceListDTO from(WhatsappInstanceMain w, String restauranteNome) {
        boolean qrPendente = w.getStatus() == WhatsappInstanceMain.Status.AGUARDANDO_QR
                && w.getQrExpiraEm() != null && w.getQrExpiraEm().isAfter(LocalDateTime.now());
        return WhatsappInstanceListDTO.builder()
                .id(w.getId())
                .restauranteId(w.getRestauranteId())
                .restauranteNome(restauranteNome)
                .instanceName(w.getInstanceName())
                .phone(w.getPhone())
                .status(w.getStatus() == null ? null : w.getStatus().name())
                .botAtivo(w.getBotAtivo())
                .conectadoEm(w.getConectadoEm())
                .atualizadoEm(w.getAtualizadoEm())
                .qrPendente(qrPendente)
                .build();
    }
}
