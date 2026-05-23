package com.mydelivery.admin.modulos.alertas.entity;

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
 * Alerta automático sobre um restaurante. Gerado pelo {@code MonitoramentoService}
 * ou manualmente por um admin.
 *
 * {@code dedupKey} = combinação tipo+restaurante (+ contexto opcional). Se já
 * existir um alerta ATIVO com o mesmo dedupKey, não cria duplicado — só atualiza
 * {@code ultimaOcorrenciaEm} e incrementa {@code ocorrencias}.
 *
 * Quando o problema some, o monitor marca como RESOLVIDO automaticamente.
 */
@Entity
@Table(name = "alerta",
        indexes = {
                @Index(name = "idx_alerta_restaurante", columnList = "restaurante_id"),
                @Index(name = "idx_alerta_status", columnList = "status"),
                @Index(name = "idx_alerta_severidade", columnList = "severidade"),
                @Index(name = "idx_alerta_criado", columnList = "criado_em"),
                @Index(name = "idx_alerta_dedup", columnList = "dedup_key,status")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Tipo tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severidade severidade;

    @Column(name = "restaurante_id", nullable = false)
    private Long restauranteId;

    @Column(nullable = false, length = 180)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    /** JSON opcional pra contexto extra (livre). */
    @Column(columnDefinition = "TEXT")
    private String dados;

    /**
     * Chave de deduplicação. Mesma chave em status ATIVO → não cria de novo.
     * Convenção: {@code "<TIPO>:<restauranteId>[:<contexto>]"}.
     */
    @Column(name = "dedup_key", nullable = false, length = 200)
    private String dedupKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "ocorrencias", nullable = false)
    private Integer ocorrencias;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "ultima_ocorrencia_em", nullable = false)
    private LocalDateTime ultimaOcorrenciaEm;

    @Column(name = "reconhecido_em")
    private LocalDateTime reconhecidoEm;

    @Column(name = "reconhecido_por")
    private Long reconhecidoPor;

    @Column(name = "resolvido_em")
    private LocalDateTime resolvidoEm;

    /** AdminUser.id, ou null se foi resolvido automaticamente pelo monitor. */
    @Column(name = "resolvido_por")
    private Long resolvidoPor;

    /** Observação livre que o admin pode deixar ao resolver/ignorar. */
    @Column(name = "observacao", columnDefinition = "TEXT")
    private String observacao;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (criadoEm == null) criadoEm = now;
        if (ultimaOcorrenciaEm == null) ultimaOcorrenciaEm = now;
        if (status == null) status = Status.ATIVO;
        if (severidade == null) severidade = Severidade.MEDIA;
        if (ocorrencias == null) ocorrencias = 1;
    }

    public enum Tipo {
        TRIAL_EXPIRANDO,
        TRIAL_EXPIRADO,
        RESTAURANTE_BLOQUEADO,
        RESTAURANTE_FECHADO_INESPERADO,
        RESTAURANTE_SEM_PEDIDOS,
        WHATSAPP_DESCONECTADO,
        PAGAMENTO_FALHOU,
        FATURA_ATRASADA,
        OUTRO
    }

    public enum Severidade {
        BAIXA, MEDIA, ALTA, CRITICA
    }

    public enum Status {
        ATIVO, RECONHECIDO, RESOLVIDO, IGNORADO
    }
}
