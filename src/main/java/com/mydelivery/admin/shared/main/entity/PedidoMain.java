package com.mydelivery.admin.shared.main.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Espelho READ-ONLY da tabela {@code pedido} do MyDelivery principal.
 *
 * Apenas os campos que o admin precisa pra calcular GMV, contagens e séries
 * temporais. Status é guardado como String pra tolerar variações no enum do main.
 *
 * IMPORTANTE: nunca chamar .save() ou .delete(). Schema do main não é mexido aqui.
 */
@Entity
@Table(name = "pedidos")
@Data
@NoArgsConstructor
public class PedidoMain {

    @Id
    private Long id;

    @Column(name = "restaurante_id")
    private Long restauranteId;

    /** Coluna real no main é "total" (não "valor_total"). Antes o admin
     *  mapeava errado e somava uma coluna fantasma criada pelo ddl-auto,
     *  sempre NULL → GMV aparecia R$ 0 mesmo com pedidos cadastrados. */
    @Column(name = "total")
    private BigDecimal valorTotal;

    /** Ex.: CRIADO, CONFIRMADO, PREPARANDO, PRONTO, A_CAMINHO, ENTREGUE, CANCELADO. */
    @Column(length = 30)
    private String status;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;
}
