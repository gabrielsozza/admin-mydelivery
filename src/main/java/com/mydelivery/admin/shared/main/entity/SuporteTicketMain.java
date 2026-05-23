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
 * Espelho READ-ONLY da tabela {@code suporte_tickets} do MyDelivery principal.
 *
 * É o ticket REAL que o dono do restaurante abre no painel — fonte única de
 * verdade. Admin lê desse mirror via JPA; escreve via {@code MainDbWriter}
 * (JdbcTemplate hardcoded), nunca por save() — mesma convenção dos demais
 * mirrors em {@code shared.main.entity}.
 *
 * Campos espelham 1:1 o entity {@code com.mydelivery.model.SuporteTicket} do
 * projeto principal. Status/Prioridade usam o enum DELE — admin traduz pra
 * sua nomenclatura interna no DTO.
 */
@Entity
@Table(name = "suporte_tickets")
@Data
@NoArgsConstructor
public class SuporteTicketMain {

    @Id
    private Long id;

    @Column(name = "restaurante_id")
    private Long restauranteId;

    @Column(length = 120)
    private String assunto;

    @Enumerated(EnumType.STRING)
    @Column(length = 25)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Prioridade prioridade;

    /** Categoria livre — sugerida automaticamente (pagamento, whatsapp, cardapio, etc). */
    @Column(length = 40)
    private String categoria;

    /** Atendente que pegou o ticket — referencia usuario.id do main DB. */
    @Column(name = "atendente_id")
    private Long atendenteId;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Column(name = "resolvido_em")
    private LocalDateTime resolvidoEm;

    /** Espelha {@code SuporteTicket.Status} do projeto principal. */
    public enum Status {
        AGUARDANDO, EM_ATENDIMENTO, RESOLVIDO, FECHADO
    }

    /** Espelha {@code SuporteTicket.Prioridade} do projeto principal. */
    public enum Prioridade {
        BAIXA, NORMAL, ALTA, URGENTE
    }
}
