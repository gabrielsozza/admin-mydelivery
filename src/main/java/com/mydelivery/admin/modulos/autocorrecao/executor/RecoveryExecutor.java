package com.mydelivery.admin.modulos.autocorrecao.executor;

import com.mydelivery.admin.modulos.autocorrecao.entity.RecoveryAction;

/**
 * Contrato pra cada tipo de auto-correção.
 *
 * Cada implementação:
 *  - declara seu {@link RecoveryAction.Tipo} via {@link #tipo()}
 *  - é stateless (idempotente quando possível)
 *  - executa em {@link #executar(RecoveryAction)} e devolve um {@link RecoveryResult}
 *  - NUNCA muda o status da RecoveryAction direto — quem faz isso é o
 *    {@code RecoveryService} a partir do resultado retornado
 */
public interface RecoveryExecutor {

    RecoveryAction.Tipo tipo();

    /**
     * Roda uma tentativa. Pode throw — o service captura e converte em falha.
     */
    RecoveryResult executar(RecoveryAction action);
}
