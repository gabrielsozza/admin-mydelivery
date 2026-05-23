package com.mydelivery.admin.modulos.tickets.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.mydelivery.admin.modulos.tickets.entity.TicketMensagem;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MensagemDTO {
    private Long id;
    private String autorTipo;
    private Long autorId;
    private String autorNome;
    private String mensagem;
    private List<String> anexos;
    private LocalDateTime criadoEm;
    private Boolean lidaPeloAdmin;
    private Boolean lidaPeloRestaurante;

    public static MensagemDTO from(TicketMensagem m) {
        return MensagemDTO.builder()
                .id(m.getId())
                .autorTipo(m.getAutorTipo() == null ? null : m.getAutorTipo().name())
                .autorId(m.getAutorId())
                .autorNome(m.getAutorNome())
                .mensagem(m.getMensagem())
                .anexos(m.getAnexos() == null ? new ArrayList<>() : new ArrayList<>(m.getAnexos()))
                .criadoEm(m.getCriadoEm())
                .lidaPeloAdmin(m.getLidaPeloAdmin())
                .lidaPeloRestaurante(m.getLidaPeloRestaurante())
                .build();
    }
}
