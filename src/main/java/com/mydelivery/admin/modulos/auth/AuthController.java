package com.mydelivery.admin.modulos.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mydelivery.admin.modulos.auth.dto.LoginRequest;
import com.mydelivery.admin.modulos.auth.dto.LoginResponse;
import com.mydelivery.admin.modulos.auth.dto.MeResponse;
import com.mydelivery.admin.modulos.auth.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Auth do admin — totalmente separado do auth dos restaurantes.
 *
 * Endpoints:
 *  - POST /api/admin/auth/login → { token, adminId, email, nome, role, expiresIn }
 *  - GET  /api/admin/auth/me    → dados do admin atual (validação de sessão)
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(authService.me(email));
    }
}
