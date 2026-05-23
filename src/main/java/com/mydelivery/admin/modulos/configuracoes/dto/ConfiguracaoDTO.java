package com.mydelivery.admin.modulos.configuracoes.dto;

import java.time.LocalDateTime;

import com.mydelivery.admin.modulos.configuracoes.entity.ConfiguracaoAdmin;

import lombok.Builder;
import lombok.Data;

/**
 * DTO de configuração. Valores marcados como sensíveis são mascarados:
 *  "ABCDEF..1234" (mostra primeiros 6 e últimos 4)
 */
@Data
@Builder
public class ConfiguracaoDTO {
    private Long id;
    private String chave;
    private String valor;
    private String descricao;
    private Boolean sensivel;
    private Boolean preenchida;
    private LocalDateTime atualizadoEm;

    public static ConfiguracaoDTO from(ConfiguracaoAdmin c) {
        boolean preenchida = c.getValor() != null && !c.getValor().isBlank();
        String valor = c.getValor();
        if (preenchida && Boolean.TRUE.equals(c.getSensivel())) {
            valor = mascarar(valor);
        }
        return ConfiguracaoDTO.builder()
                .id(c.getId())
                .chave(c.getChave())
                .valor(valor)
                .descricao(c.getDescricao())
                .sensivel(c.getSensivel())
                .preenchida(preenchida)
                .atualizadoEm(c.getAtualizadoEm())
                .build();
    }

    private static String mascarar(String v) {
        if (v == null || v.length() <= 10) return "********";
        return v.substring(0, 6) + "…" + v.substring(v.length() - 4);
    }
}
