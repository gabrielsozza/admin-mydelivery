package com.mydelivery.admin;

import java.util.TimeZone;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Painel administrativo do MyDelivery.
 *
 * Projeto separado do backend dos restaurantes (mydelivery-api). Convive com ele
 * conectando em DOIS bancos:
 *  - admin_mydelivery (escrita)   → dados próprios do admin (tickets, alertas, configs)
 *  - mydelivery_db (LEITURA only) → dados dos restaurantes / pedidos / pagamentos
 *
 * Deploy: Railway service separado, frontend em admin.mydeliveryfood.com.br.
 */
@SpringBootApplication
@EnableScheduling
public class MydeliveryAdminApiApplication {

    /**
     * Força timezone Brasil. Sem isso, JVM em container Linux usa UTC
     * e qualquer LocalDateTime.now() vem com -3h, quebrando comparações de validade
     * (cupons, planos, expirações). Igual fizemos no projeto principal.
     */
    @PostConstruct
    void initTimezone() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
    }

    public static void main(String[] args) {
        SpringApplication.run(MydeliveryAdminApiApplication.class, args);
    }
}
