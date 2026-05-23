package com.mydelivery.admin.modulos.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {
    private String token;
    private Long adminId;
    private String email;
    private String nome;
    private String role;
    private long expiresIn; // ms
}
