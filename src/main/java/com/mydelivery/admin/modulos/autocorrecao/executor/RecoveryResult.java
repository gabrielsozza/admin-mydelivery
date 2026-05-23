package com.mydelivery.admin.modulos.autocorrecao.executor;

/**
 * Resultado de uma tentativa de execução. Imutável.
 *
 *  - {@code ok}=true        → marca RecoveryAction como SUCESSO
 *  - {@code ok}=false       → reagenda (ou marca FALHOU se estourou tentativas)
 *  - {@code mensagem}       → vai pro campo {@code resultado} JSON-ish
 *  - {@code detalheJson}    → opcional, JSON livre com mais contexto
 */
public record RecoveryResult(boolean ok, String mensagem, String detalheJson) {

    public static RecoveryResult sucesso(String mensagem) {
        return new RecoveryResult(true, mensagem, null);
    }

    public static RecoveryResult sucessoComDetalhe(String mensagem, String detalheJson) {
        return new RecoveryResult(true, mensagem, detalheJson);
    }

    public static RecoveryResult falha(String mensagem) {
        return new RecoveryResult(false, mensagem, null);
    }

    public static RecoveryResult falhaComDetalhe(String mensagem, String detalheJson) {
        return new RecoveryResult(false, mensagem, detalheJson);
    }
}
