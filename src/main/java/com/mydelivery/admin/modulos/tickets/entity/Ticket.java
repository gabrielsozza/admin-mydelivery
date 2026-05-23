package com.mydelivery.admin.modulos.tickets.entity;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ticket de suporte aberto por um restaurante (ou criado pelo próprio admin
 * em nome dele). Toda conversa do thread vai em {@link TicketMensagem}.
 *
 *  status:     ABERTO → EM_ANDAMENTO → AGUARDANDO_CLIENTE → RESOLVIDO → FECHADO
 *  prioridade: BAIXA / MEDIA / ALTA / CRITICA
 *  categoria:  PAGAMENTO / WHATSAPP / CARDAPIO / PEDIDO / FATURAMENTO / OUTRO
 *
 * {@code restauranteId} aponta pra {@code restaurante.id} do DB principal —
 * sem FK física porque os dois DBs são separados; integridade é garantida
 * só validando na hora de criar.
 */
@Entity
@Table(name = "ticket", indexes = {
        @Index(name = "idx_ticket_restaurante", columnList = "restaurante_id"),
        @Index(name = "idx_ticket_status", columnList = "status"),
        @Index(name = "idx_ticket_atribuido", columnList = "atribuido_a")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurante_id", nullable = false)
    private Long restauranteId;

    @Column(nullable = false, length = 180)
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Prioridade prioridade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Categoria categoria;

    /** AdminUser.id ou null se o ticket veio do restaurante (via integração futura). */
    @Column(name = "criado_por_admin_id")
    private Long criadoPorAdminId;

    /** Snapshot do nome de quem criou (admin ou restaurante) — pra histórico. */
    @Column(name = "criado_por_nome", length = 120)
    private String criadoPorNome;

    /** AdminUser.id que está cuidando, null se ninguém assumiu ainda. */
    @Column(name = "atribuido_a")
    private Long atribuidoA;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @Column(name = "fechado_em")
    private LocalDateTime fechadoEm;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (criadoEm == null) criadoEm = now;
        atualizadoEm = now;
        if (status == null) status = Status.ABERTO;
        if (prioridade == null) prioridade = Prioridade.MEDIA;
        if (categoria == null) categoria = Categoria.OUTRO;
    }

    @PreUpdate
    void preUpdate() {
        atualizadoEm = LocalDateTime.now();
        if ((status == Status.RESOLVIDO || status == Status.FECHADO) && fechadoEm == null) {
            fechadoEm = atualizadoEm;
        }
    }

    public enum Status {
        ABERTO, EM_ANDAMENTO, AGUARDANDO_CLIENTE, RESOLVIDO, FECHADO
    }

    public enum Prioridade {
        BAIXA, MEDIA, ALTA, CRITICA
    }

    public enum Categoria {
        PAGAMENTO, WHATSAPP, CARDAPIO, PEDIDO, FATURAMENTO, OUTRO
    }
}
