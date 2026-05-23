package com.mydelivery.admin.modulos.whatsapp.dto;

import java.time.LocalDateTime;

import com.mydelivery.admin.shared.main.entity.WhatsappInstanceMain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WhatsappInstanceDetalheDTO {
    private Long id;
    private Long restauranteId;
    private String restauranteNome;
    private String instanceName;
    private String phone;
    private String status;
    private Boolean botAtivo;
    private String qrCode;
    private LocalDateTime qrExpiraEm;
    private Boolean qrPendente;
    private LocalDateTime conectadoEm;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    public static WhatsappInstanceDetalheDTO from(WhatsappInstanceMain w, String restauranteNome) {
        boolean qrPendente = w.getStatus() == WhatsappInstanceMain.Status.AGUARDANDO_QR
                && w.getQrExpiraEm() != null && w.getQrExpiraEm().isAfter(LocalDateTime.now());
        return WhatsappInstanceDetalheDTO.builder()
                .id(w.getId())
                .restauranteId(w.getRestauranteId())
                .restauranteNome(restauranteNome)
                .instanceName(w.getInstanceName())
                .phone(w.getPhone())
                .status(w.getStatus() == null ? null : w.getStatus().name())
                .botAtivo(w.getBotAtivo())
                .qrCode(w.getQrCode())
                .qrExpiraEm(w.getQrExpiraEm())
                .qrPendente(qrPendente)
                .conectadoEm(w.getConectadoEm())
                .criadoEm(w.getCriadoEm())
                .atualizadoEm(w.getAtualizadoEm())
                .build();
    }
}
