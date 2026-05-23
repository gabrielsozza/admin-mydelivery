package com.mydelivery.admin.modulos.autocorrecao.executor;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.mydelivery.admin.modulos.autocorrecao.entity.RecoveryAction;
import com.mydelivery.admin.modulos.autocorrecao.service.MainDbWriter;
import com.mydelivery.admin.shared.main.entity.AssinaturaMain;
import com.mydelivery.admin.shared.main.entity.RestauranteMain;
import com.mydelivery.admin.shared.main.repository.AssinaturaMainRepository;
import com.mydelivery.admin.shared.main.repository.RestauranteMainRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Auto-correção do tipo {@link RecoveryAction.Tipo#BLOQUEAR_INADIMPLENTE}.
 *
 * Antes de bloquear, re-verifica no main DB:
 *  1. Restaurante existe e está ATIVO
 *  2. Tem Assinatura com status INADIMPLENTE há mais de {@code diasTolerancia} dias
 *     (medido via {@code validaAte} ou {@code proximaCobranca})
 *
 * Se algo já mudou (assinatura voltou pra ATIVA, restaurante já bloqueado por
 * outro motivo, etc), considera SUCESSO sem ação — o problema sumiu.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BloqueioInadimplenciaExecutor implements RecoveryExecutor {

    private final MainDbWriter writer;
    private final RestauranteMainRepository restauranteRepo;
    private final AssinaturaMainRepository assinaturaRepo;

    @Value("${admin.inadimplencia.dias-tolerancia:7}")
    private int diasTolerancia;

    @Override
    public RecoveryAction.Tipo tipo() {
        return RecoveryAction.Tipo.BLOQUEAR_INADIMPLENTE;
    }

    @Override
    public RecoveryResult executar(RecoveryAction action) {
        Long restauranteId = action.getRestauranteId();
        RestauranteMain r = restauranteRepo.findById(restauranteId).orElse(null);

        if (r == null) {
            return RecoveryResult.falha("Restaurante " + restauranteId + " não existe mais");
        }
        if (r.getStatus() != RestauranteMain.Status.ATIVO) {
            log.info("[Recovery] restaurante {} não está mais ATIVO ({}). Sem ação.",
                    restauranteId, r.getStatus());
            return RecoveryResult.sucesso("Estado mudou antes da execução (status=" + r.getStatus() + ").");
        }

        AssinaturaMain a = assinaturaRepo.findFirstByRestauranteIdOrderByIdDesc(restauranteId).orElse(null);
        if (a == null) {
            return RecoveryResult.sucesso("Restaurante sem assinatura ativa — nada a bloquear.");
        }
        if (a.getStatus() != AssinaturaMain.Status.INADIMPLENTE) {
            return RecoveryResult.sucesso(
                    "Assinatura saiu de INADIMPLENTE (agora=" + a.getStatus() + "). Problema resolvido.");
        }

        // Quando a assinatura virou inadimplente? Tolerância: validaAte ou proximaCobranca passou há > N dias
        LocalDateTime referencia = a.getValidaAte() != null ? a.getValidaAte()
                : (a.getProximaCobranca() != null ? a.getProximaCobranca() : null);
        if (referencia != null) {
            long diasAtraso = Duration.between(referencia, LocalDateTime.now()).toDays();
            if (diasAtraso < diasTolerancia) {
                return RecoveryResult.falha(
                        "Inadimplente há " + diasAtraso + " dias — abaixo da tolerância (" + diasTolerancia + ")");
            }
        }

        try {
            String motivo = "Inadimplência: assinatura " + a.getId()
                    + " (plano " + a.getPlano() + ") INADIMPLENTE há mais de " + diasTolerancia + " dias";
            int linhas = writer.bloquearPorInadimplencia(restauranteId, motivo);
            if (linhas == 1) {
                return RecoveryResult.sucesso(
                        "Restaurante bloqueado por inadimplência (assinatura " + a.getId() + ").");
            }
            return RecoveryResult.sucesso(
                    "Status não era ATIVO no momento do UPDATE (race). Ação idempotente.");
        } catch (Exception e) {
            log.error("[Recovery] falha no UPDATE bloqueio inadimplência restauranteId={}: {}",
                    restauranteId, e.getMessage(), e);
            return RecoveryResult.falha("Erro no DB: " + e.getMessage());
        }
    }
}
