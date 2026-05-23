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
 * Espelho READ-ONLY de {@code whatsapp_instances} no main.
 *
 * 1 por restaurante. O monitor admin lê isso pra mostrar status da conexão
 * Evolution API, QR pendente, desconexões, etc.
 */
@Entity
@Table(name = "whatsapp_instances")
@Data
@NoArgsConstructor
public class WhatsappInstanceMain {

    @Id
    private Long id;

    @Column(name = "restaurante_id")
    private Long restauranteId;

    @Column(name = "instance_name", length = 80)
    private String instanceName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(length = 25)
    private Status status;

    @Column(name = "qr_code", columnDefinition = "TEXT")
    private String qrCode;

    @Column(name = "qr_expira_em")
    private LocalDateTime qrExpiraEm;

    @Column(name = "conectado_em")
    private LocalDateTime conectadoEm;

    @Column(name = "bot_ativo")
    private Boolean botAtivo;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    public enum Status {
        NOVA, AGUARDANDO_QR, CONECTADA, DESCONECTADA, ERRO
    }
}
