package com.mydelivery.admin.modulos.dashboard;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint público de health check do admin.
 *
 * Verifica:
 *  - app subiu (responde 200)
 *  - conexão com admin_mydelivery (DB próprio)
 *  - conexão com mydelivery_db (DB do projeto principal)
 *
 * Útil tanto pra Railway monitorar quanto pra debug rápido no navegador.
 */
@RestController
@RequestMapping("/api/admin")
public class HealthController {

    private final DataSource adminDataSource;
    private final DataSource mainDataSource;

    public HealthController(
            @Qualifier("adminDataSource") DataSource adminDataSource,
            @Qualifier("mainDataSource") DataSource mainDataSource) {
        this.adminDataSource = adminDataSource;
        this.mainDataSource = mainDataSource;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "ok");
        resp.put("servico", "admin-mydelivery-api");
        resp.put("timestamp", LocalDateTime.now().toString());
        resp.put("dbAdmin", pingDataSource(adminDataSource));
        resp.put("dbMain", pingDataSource(mainDataSource));
        return resp;
    }

    /** Tenta abrir conexão e fechar. Retorna "ok" ou a mensagem de erro. */
    private String pingDataSource(DataSource ds) {
        try (var conn = ds.getConnection()) {
            return conn.isValid(2) ? "ok" : "invalid";
        } catch (Exception e) {
            return "erro: " + e.getMessage();
        }
    }
}
