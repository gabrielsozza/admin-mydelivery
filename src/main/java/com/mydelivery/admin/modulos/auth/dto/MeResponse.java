package com.mydelivery.admin.modulos.auth.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Retornado por GET /api/admin/auth/me — front usa pra validar sessão. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MeResponse {
    private Long id;
    private String email;
    private String nome;
    private String role;
    private Boolean ativo;
    private LocalDateTime ultimoLoginEm;
}
