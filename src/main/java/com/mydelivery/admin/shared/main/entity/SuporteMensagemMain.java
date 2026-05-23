package com.mydelivery.admin.shared.main.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Espelho READ-ONLY da tabela {@code suporte_mensagens}. Cada mensagem
 * pertence a um ticket via {@code ticket_id}.
 *
 * O autor pode ser RESTAURANTE (dono), ATENDENTE (admin/suporte) ou SISTEMA.
 * Anexos ficam em {@code suporte_anexos} — V1 do admin ignora (lista vazia).
 */
@Entity
@Table(name = "suporte_mensagens")
@Data
@NoArgsConstructor
public class SuporteMensagemMain {

    @Id
    private Long id;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Autor autor;

    @Column(name = "autor_nome", length = 80)
    private String autorNome;

    @Column(columnDefinition = "TEXT")
    private String texto;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    public enum Autor { RESTAURANTE, SISTEMA, ATENDENTE }
}
