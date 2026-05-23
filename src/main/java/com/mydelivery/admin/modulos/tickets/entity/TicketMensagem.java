package com.mydelivery.admin.modulos.tickets.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mensagem (turn) dentro de um ticket. O thread do ticket é a lista ordenada
 * dessas mensagens.
 *
 * {@code anexos} guarda URLs do Cloudinary (upload feito no frontend usando
 * unsigned upload preset, igual no main MyDelivery). O backend só persiste o
 * URL final.
 */
@Entity
@Table(name = "ticket_mensagem", indexes = {
        @Index(name = "idx_msg_ticket", columnList = "ticket_id"),
        @Index(name = "idx_msg_criado", columnList = "criado_em")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketMensagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "autor_tipo", nullable = false, length = 20)
    private AutorTipo autorTipo;

    /** AdminUser.id se ADMIN, restaurante.id se RESTAURANTE, null se sistema. */
    @Column(name = "autor_id")
    private Long autorId;

    /** Snapshot do nome — não some se o user for removido. */
    @Column(name = "autor_nome", length = 120)
    private String autorNome;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String mensagem;

    /** URLs do Cloudinary. Lista mesmo vazia, nunca null. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ticket_mensagem_anexo",
            joinColumns = @JoinColumn(name = "mensagem_id"))
    @Column(name = "url", length = 500)
    @Builder.Default
    private List<String> anexos = new ArrayList<>();

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "lida_pelo_admin", nullable = false)
    private Boolean lidaPeloAdmin;

    @Column(name = "lida_pelo_restaurante", nullable = false)
    private Boolean lidaPeloRestaurante;

    @PrePersist
    void prePersist() {
        if (criadoEm == null) criadoEm = LocalDateTime.now();
        if (autorTipo == null) autorTipo = AutorTipo.ADMIN;
        if (lidaPeloAdmin == null) lidaPeloAdmin = autorTipo == AutorTipo.ADMIN;
        if (lidaPeloRestaurante == null) lidaPeloRestaurante = autorTipo == AutorTipo.RESTAURANTE;
        if (anexos == null) anexos = new ArrayList<>();
    }

    public enum AutorTipo {
        ADMIN, RESTAURANTE, SISTEMA
    }
}
