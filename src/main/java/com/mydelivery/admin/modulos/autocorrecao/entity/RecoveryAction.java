package com.mydelivery.admin.modulos.autocorrecao.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ação automática de correção tentada (ou pendente) para um problema detectado.
 *
 * Fluxo:
 *  1. Monitor detecta problema recuperável.
 *  2. Em vez de criar alerta direto, chama {@code RecoveryService.solicitar(...)}.
 *  3. RecoveryService cria uma RecoveryAction PENDENTE (com dedupKey, evita duplicar).
 *  4. RecoveryScheduler pega as PENDENTES e roda o {@code RecoveryExecutor} correspondente.
 *  5. SUCESSO  → fim. Sem alerta.
 *  6. FALHA    → reagenda com backoff. Após {@code MAX_TENTATIVAS} falhas vira FALHOU e EMITE ALERTA.
 *  7. CANCELADA → admin descartou manualmente.
 *
 * O campo {@code resultado} guarda JSON livre com diagnóstico (HTTP code, mensagem do banco, etc).
 */
@Entity
@Table(name = "recovery_action", indexes = {
        @Index(name = "idx_recovery_status", columnList = "status"),
        @Index(name = "idx_recovery_proxima", columnList = "proxima_tentativa_em"),
        @Index(name = "idx_recovery_restaurante", columnList = "restaurante_id"),
        @Index(name = "idx_recovery_dedup", columnList = "dedup_key,status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecoveryAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Tipo tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "restaurante_id", nullable = false)
    private Long restauranteId;

    /** Convenção: {@code "<TIPO>:<restauranteId>"}. Mesma chave em status PENDENTE/EXECUTANDO/AGUARDANDO_RETRY não cria nova. */
    @Column(name = "dedup_key", nullable = false, length = 200)
    private String dedupKey;

    /** JSON livre com payload pra execução (ex.: motivo do bloqueio, valor, etc). */
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "tentativas", nullable = false)
    private Integer tentativas;

    @Column(name = "max_tentativas", nullable = false)
    private Integer maxTentativas;

    @Column(name = "ultima_tentativa_em")
    private LocalDateTime ultimaTentativaEm;

    /** Quando o scheduler deve tentar de novo. Se status=PENDENTE e <=now, tá pronto. */
    @Column(name = "proxima_tentativa_em")
    private LocalDateTime proximaTentativaEm;

    /** JSON com log da última execução: { "ok": false, "erro": "..." } */
    @Column(name = "resultado", columnDefinition = "TEXT")
    private String resultado;

    /** Quem solicitou: "MONITOR" (auto) ou AdminUser.id como string. */
    @Column(name = "solicitado_por", length = 50)
    private String solicitadoPor;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "finalizado_em")
    private LocalDateTime finalizadoEm;

    /** Se virou alerta após falhar, referência aqui pra rastreio. */
    @Column(name = "alerta_emitido_id")
    private Long alertaEmitidoId;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (criadoEm == null) criadoEm = now;
        if (status == null) status = Status.PENDENTE;
        if (tentativas == null) tentativas = 0;
        if (maxTentativas == null) maxTentativas = 3;
        if (proximaTentativaEm == null) proximaTentativaEm = now;
    }

    public enum Tipo {
        /** Restaurante com status=TRIAL e trial expirado → mover pra BLOQUEADO com motivo. */
        BLOQUEAR_TRIAL_EXPIRADO,
        /** Restaurante com fatura VENCIDA há > X dias → mover pra BLOQUEADO. */
        BLOQUEAR_INADIMPLENTE,
        // futuras: REABRIR_RESTAURANTE, RECONECTAR_WHATSAPP, COBRAR_FATURA_VENCIDA, etc.
    }

    public enum Status {
        PENDENTE,           // criada, aguardando 1ª tentativa
        EXECUTANDO,         // scheduler pegou e está rodando agora
        AGUARDANDO_RETRY,   // falhou, vai tentar de novo depois
        SUCESSO,            // resolvido
        FALHOU,             // estourou max_tentativas
        CANCELADA           // admin cancelou manualmente
    }
}
