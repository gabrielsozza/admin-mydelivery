package com.mydelivery.admin.modulos.autocorrecao.executor;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.mydelivery.admin.modulos.autocorrecao.entity.RecoveryAction;
import com.mydelivery.admin.modulos.autocorrecao.service.MainDbWriter;
import com.mydelivery.admin.shared.main.entity.RestauranteMain;
import com.mydelivery.admin.shared.main.repository.RestauranteMainRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Auto-correção do tipo {@link RecoveryAction.Tipo#BLOQUEAR_TRIAL_EXPIRADO}.
 *
 * Re-verifica a condição antes de executar (defesa em profundidade — entre o
 * monitor detectar e o scheduler rodar pode passar tempo e o estado mudar).
 *
 * Sucesso = 1 linha alterada no main. 0 linhas = idempotente (alguém já mudou
 * o status manualmente), também consideramos SUCESSO (problema sumiu).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BloqueioTrialExpiradoExecutor implements RecoveryExecutor {

    private final MainDbWriter writer;
    private final RestauranteMainRepository restauranteRepo;

    @Override
    public RecoveryAction.Tipo tipo() {
        return RecoveryAction.Tipo.BLOQUEAR_TRIAL_EXPIRADO;
    }

    @Override
    public RecoveryResult executar(RecoveryAction action) {
        Long restauranteId = action.getRestauranteId();
        RestauranteMain r = restauranteRepo.findById(restauranteId).orElse(null);

        if (r == null) {
            return RecoveryResult.falha("Restaurante " + restauranteId + " não existe mais");
        }

        // Re-checa condição: ainda é TRIAL e trial já expirou?
        if (r.getStatus() != RestauranteMain.Status.TRIAL) {
            log.info("[Recovery] restaurante {} não está mais TRIAL ({}), nada a fazer",
                    restauranteId, r.getStatus());
            return RecoveryResult.sucesso(
                    "Estado mudou antes da execução (status=" + r.getStatus() + "). Sem ação.");
        }
        if (r.getTrialExpiraEm() == null || !r.getTrialExpiraEm().isBefore(LocalDateTime.now())) {
            return RecoveryResult.falha(
                    "Trial ainda válido (expira_em=" + r.getTrialExpiraEm() + "). Abortado.");
        }

        try {
            int linhas = writer.bloquearPorTrialExpirado(restauranteId,
                    "Trial expirado em " + r.getTrialExpiraEm() + " (bloqueio automático)");
            if (linhas == 1) {
                return RecoveryResult.sucesso("Restaurante bloqueado por trial expirado.");
            } else {
                // 0 linhas = status não era TRIAL (race). Ok, problema sumiu.
                return RecoveryResult.sucesso(
                        "Status já não era TRIAL no momento do UPDATE (race). Ação idempotente.");
            }
        } catch (Exception e) {
            log.error("[Recovery] falha no UPDATE bloqueio trial restauranteId={}: {}",
                    restauranteId, e.getMessage(), e);
            return RecoveryResult.falha("Erro no DB: " + e.getMessage());
        }
    }
}
