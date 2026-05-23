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
 * Espelho READ-ONLY de {@code pagamentos_mensalidade} (faturas do SaaS) do main.
 *
 * São as faturas REAIS — cada assinatura gera uma quando precisa de cobrança.
 * Status: PENDENTE | PAGO | CANCELADO
 *
 * Diferente de {@code Pagamento} do main, que é pagamento de PEDIDO (cliente
 * pagando restaurante). Este aqui é o restaurante pagando MyDelivery.
 */
@Entity
@Table(name = "pagamentos_mensalidade")
@Data
@NoArgsConstructor
public class PagamentoMensalidadeMain {

    @Id
    private Long id;

    @Column(name = "restaurante_id")
    private Long restauranteId;

    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Status status;

    @Column(name = "metodo_pagamento")
    private String metodoPagamento;

    @Column(name = "referencia_gateway", length = 200)
    private String referenciaGateway;

    @Column(name = "pago_em")
    private LocalDateTime pagoEm;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    public enum Status {
        PENDENTE, PAGO, CANCELADO
    }
}
