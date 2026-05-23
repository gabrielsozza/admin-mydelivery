package com.mydelivery.admin.shared.main.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Espelho READ-ONLY de {@code suporte_anexos}. Cada anexo pertence a uma
 * mensagem (ticket_mensagem). URL aponta pra Cloudinary nos anexos novos;
 * legados podem ter caminho relativo (./uploads/...).
 */
@Entity
@Table(name = "suporte_anexos")
@Data
@NoArgsConstructor
public class SuporteAnexoMain {

    @Id
    private Long id;

    @Column(name = "mensagem_id")
    private Long mensagemId;

    @Column(name = "nome_arquivo")
    private String nomeArquivo;

    @Column(name = "nome_original")
    private String nomeOriginal;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "tamanho_bytes")
    private Long tamanhoBytes;

    private String url;
}
