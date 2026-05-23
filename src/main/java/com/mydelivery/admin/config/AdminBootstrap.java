package com.mydelivery.admin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.mydelivery.admin.modulos.auth.entity.AdminUser;
import com.mydelivery.admin.modulos.auth.repository.AdminUserRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Cria um admin padrão na PRIMEIRA inicialização (se não existir ninguém no DB).
 *
 * Credenciais vêm do env (ADMIN_BOOTSTRAP_EMAIL + ADMIN_BOOTSTRAP_PASSWORD).
 * Em produção: setar essas vars no Railway só uma vez. Depois pode até remover
 * — o bootstrap só cria se a tabela admin_user estiver VAZIA.
 *
 * Pra dev local, defaults seguros mas trocáveis via .env.
 */
@Slf4j
@Configuration
public class AdminBootstrap {

    @Bean
    public ApplicationRunner inicializarAdminPadrao(
            AdminUserRepository repo,
            PasswordEncoder encoder,
            @Value("${admin.bootstrap.email:admin@mydelivery.app}") String email,
            @Value("${admin.bootstrap.password:trocar-essa-senha-no-primeiro-login}") String senha,
            @Value("${admin.bootstrap.nome:Admin MyDelivery}") String nome
    ) {
        return args -> {
            if (repo.count() > 0) return; // já tem alguém, não recria

            AdminUser admin = AdminUser.builder()
                    .email(email.toLowerCase().trim())
                    .senhaHash(encoder.encode(senha))
                    .nome(nome)
                    .role(AdminUser.Role.ADMIN)
                    .ativo(true)
                    .build();
            repo.save(admin);

            log.warn("══════════════════════════════════════════════════");
            log.warn(" ADMIN PADRÃO CRIADO ");
            log.warn(" email: {} ", email);
            log.warn(" senha: definida via env ADMIN_BOOTSTRAP_PASSWORD ");
            log.warn(" ⚠️  TROQUE A SENHA APÓS O PRIMEIRO LOGIN");
            log.warn("══════════════════════════════════════════════════");
        };
    }
}
