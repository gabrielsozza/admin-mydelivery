package com.mydelivery.admin.shared.main.entity;

import java.math.BigDecimal;
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
 * Espelho READ-ONLY de {@code pagamentos} no main (pagamento de PEDIDO).
 *
 * Diferente de {@code PagamentoMensalidadeMain} (que é a fatura SaaS).
 * Aqui registra o cliente final pagando o pedido no restaurante.
 *
 * Admin usa pra detectar pagamentos com erro: status diferente de APROVADO,
 * tipicamente RECUSADO/EXPIRADO. {@code mpStatusDetail} traz o motivo do MP
 * (ex: "cc_rejected_call_for_authorize", "rejected_high_risk", "pix_expired").
 */
@Entity
@Table(name = "pagamentos")
@Data
@NoArgsConstructor
public class PagamentoPedidoMain {

    @Id
    private Long id;

    /**
     * FK pra pedido — guardamos só o id, sem mapear a entidade Pedido (evita
     * coupling de schema).
     */
    @Column(name = "pedido_id")
    private Long pedidoId;

    /** PIX, DINHEIRO, CARTAO_CREDITO, etc. */
    @Column(length = 30)
    private String metodo;

    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private Status status;

    @Column(name = "mp_payment_id", length = 100)
    private String mpPaymentId;

    @Column(name = "mp_status_detail", length = 200)
    private String mpStatusDetail;

    @Column(name = "expira_em")
    private LocalDateTime expiraEm;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "aprovado_em")
    private LocalDateTime aprovadoEm;

    public enum Status {
        PENDENTE, APROVADO, RECUSADO, EXPIRADO, CANCELADO
    }
}
