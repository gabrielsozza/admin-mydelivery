package com.mydelivery.admin.shared.main.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Espelho READ-ONLY da tabela {@code usuarios} do MyDelivery principal.
 *
 * Usado pra exibir nome, email e telefone do dono na tela de detalhe do
 * restaurante (admin). NUNCA escreve aqui — apenas leitura.
 */
@Entity
@Table(name = "usuarios")
@Data
@NoArgsConstructor
public class UsuarioMain {

    @Id
    private Long id;

    private String nome;
    private String email;
    private String telefone;
}
