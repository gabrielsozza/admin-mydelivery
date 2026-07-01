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
 * Espelho READ-ONLY da tabela {@code restaurante} do MyDelivery principal.
 *
 * Replica APENAS os campos que o admin precisa visualizar. Sem relations
 * (@ManyToOne, @OneToMany) pra evitar coupling com o schema do main —
 * se a tabela tiver colunas que não estão aqui, JPA ignora.
 *
 * IMPORTANTE: nunca chamar .save() ou .delete() — o EntityManager dessa
 * entidade está configurado pra ler do DB principal e o app não deve
 * escrever nele.
 */
@Entity
@Table(name = "restaurantes")
@Data
@NoArgsConstructor
public class RestauranteMain {

    @Id
    private Long id;

    /** FK pro Usuario dono do restaurante (1:1). Usado pra puxar email/telefone do dono. */
    @Column(name = "usuario_id")
    private Long usuarioId;

    private String nome;
    private String slug;
    private String cnpj;

    @Column(name = "logo_url")
    private String logoUrl;

    private String telefone;
    private String endereco;
    private String cidade;
    private String estado;

    private Boolean aberto;

    @Column(name = "tempo_entrega")
    private Integer tempoEntrega;

    @Column(name = "taxa_entrega")
    private BigDecimal taxaEntrega;

    @Column(name = "pedido_minimo")
    private BigDecimal pedidoMinimo;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "trial_expira_em")
    private LocalDateTime trialExpiraEm;

    @Column(name = "bloqueado_em")
    private LocalDateTime bloqueadoEm;

    @Column(name = "motivo_bloqueio")
    private String motivoBloqueio;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    // ── Vínculo com afiliado (snapshot imutável salvo no cadastro) ──
    @Column(name = "afiliado_codigo")
    private String afiliadoCodigo;

    @Column(name = "afiliado_id_snap")
    private Long afiliadoIdSnap;

    @Column(name = "afiliado_nome_snap")
    private String afiliadoNomeSnap;

    @Column(name = "afiliado_email_snap")
    private String afiliadoEmailSnap;

    @Column(name = "afiliado_comissao_snap")
    private java.math.BigDecimal afiliadoComissaoSnap;

    @Column(name = "afiliado_vinculado_em")
    private LocalDateTime afiliadoVinculadoEm;

    /** Espelha o enum Status do projeto principal. */
    public enum Status {
        ATIVO, BLOQUEADO, TRIAL, CANCELADO
    }
}
