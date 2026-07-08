package com.mydelivery.admin.modulos.restaurantes.service;

import java.security.SecureRandom;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.mydelivery.admin.modulos.autocorrecao.service.MainDbWriter;
import com.mydelivery.admin.modulos.restaurantes.dto.RestauranteDetalheDTO;
import com.mydelivery.admin.modulos.restaurantes.dto.RestauranteListDTO;
import com.mydelivery.admin.shared.exception.NotFoundException;
import com.mydelivery.admin.shared.main.entity.RestauranteMain;
import com.mydelivery.admin.shared.main.entity.UsuarioMain;
import com.mydelivery.admin.shared.main.repository.RestauranteMainRepository;
import com.mydelivery.admin.shared.main.repository.UsuarioMainRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantesService {

    private final RestauranteMainRepository restauranteRepo;
    private final UsuarioMainRepository usuarioRepo;
    private final MainDbWriter writer;
    private final PasswordEncoder passwordEncoder;

    private static final String SENHA_ALFABETO =
        "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RNG = new SecureRandom();

    /**
     * Busca paginada com filtros.
     *
     * @param statusStr ATIVO/TRIAL/BLOQUEADO/CANCELADO (case-insensitive), null = todos
     * @param q         busca por nome, slug ou telefone (parcial)
     * @param page      0-based
     * @param size      máx 100
     */
    public Page<RestauranteListDTO> listar(String statusStr, String q, int page, int size) {
        RestauranteMain.Status status = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                status = RestauranteMain.Status.valueOf(statusStr.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // status inválido = ignora filtro (não quebra a busca)
            }
        }
        if (size <= 0 || size > 100) size = 25;
        if (page < 0) page = 0;
        Pageable pageable = PageRequest.of(page, size);

        return restauranteRepo.buscar(status, q, pageable).map(RestauranteListDTO::from);
    }

    public RestauranteDetalheDTO detalhe(Long id) {
        RestauranteMain r = restauranteRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Restaurante não encontrado"));

        // Puxa dados do dono (Usuario) — falha silenciosa se não achar
        String donoNome = null, donoEmail = null, donoTelefone = null;
        if (r.getUsuarioId() != null) {
            UsuarioMain u = usuarioRepo.findById(r.getUsuarioId()).orElse(null);
            if (u != null) {
                donoNome = u.getNome();
                donoEmail = u.getEmail();
                donoTelefone = u.getTelefone();
            }
        }
        return RestauranteDetalheDTO.from(r, donoNome, donoEmail, donoTelefone);
    }

    /**
     * Redefine senha do dono do restaurante (uso emergencial pelo suporte).
     * Se {@code novaSenha} vier vazia, gera uma senha aleatória legível.
     * Retorna a senha em texto puro pra admin comunicar ao cliente — NÃO fica logada.
     *
     * @return Map com: ok, novaSenha (texto puro), email/nome do dono, mensagem
     */
    public Map<String, Object> redefinirSenha(Long id, String novaSenha) {
        RestauranteMain r = restauranteRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Restaurante não encontrado"));

        // Senha custom OU gerada
        String senhaPlana;
        boolean gerada = (novaSenha == null || novaSenha.isBlank());
        if (gerada) {
            senhaPlana = gerarSenhaAleatoria(10);
        } else {
            senhaPlana = novaSenha.trim();
            if (senhaPlana.length() < 6) {
                throw new IllegalArgumentException("Senha precisa ter no mínimo 6 caracteres");
            }
            if (senhaPlana.length() > 60) {
                throw new IllegalArgumentException("Senha muito longa (máx 60 caracteres)");
            }
        }

        String hash = passwordEncoder.encode(senhaPlana);
        int linhas = writer.redefinirSenhaDoRestaurante(id, hash);
        if (linhas == 0) {
            throw new IllegalStateException("Não foi possível redefinir senha (usuário sem vínculo)");
        }

        // Pega email/nome do dono pra admin saber pra quem mandar
        String donoEmail = null, donoNome = null;
        if (r.getUsuarioId() != null) {
            var u = usuarioRepo.findById(r.getUsuarioId()).orElse(null);
            if (u != null) {
                donoEmail = u.getEmail();
                donoNome = u.getNome();
            }
        }

        log.warn("[Restaurante] REDEFINIR SENHA id={} nome={} donoEmail={} gerada={}",
                id, r.getNome(), donoEmail, gerada);

        return Map.of(
            "ok", true,
            "restauranteId", id,
            "restauranteNome", r.getNome() != null ? r.getNome() : "",
            "donoNome", donoNome != null ? donoNome : "",
            "donoEmail", donoEmail != null ? donoEmail : "",
            "novaSenha", senhaPlana,
            "geradaAutomaticamente", gerada,
            "mensagem", "Senha redefinida. Comunique ao cliente — esta tela não vai mostrar a senha de novo."
        );
    }

    /** Senha aleatória legível (sem caracteres ambíguos como 0/O/1/l). */
    private static String gerarSenhaAleatoria(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(SENHA_ALFABETO.charAt(RNG.nextInt(SENHA_ALFABETO.length())));
        }
        return sb.toString();
    }

    /**
     * Apaga o restaurante DEFINITIVAMENTE — incluindo todos os dados associados
     * (pedidos, produtos, cupons, mesas/QR codes, assinatura, suporte, usuário dono).
     * Operação irreversível. Usado pra limpar cadastros lixo de curiosos.
     *
     * @return map com contadores por tabela (auditoria)
     */
    /**
     * Vincula manualmente um restaurante a um afiliado (ou remove o vínculo).
     *
     * Contexto: normalmente o vínculo é criado no cadastro (link com ?ref=X)
     * e é imutável pra o dono do restaurante — ele não muda quem tem direito
     * à comissão dele. Este método permite o ADMIN corrigir situações
     * excepcionais: loja que entrou sem link, digitou errado, ou precisa
     * ser realocada.
     *
     * Dados persistidos como snapshot: código + id + nome + email + comissão
     * ficam gravados no restaurante mesmo se o afiliado for depois excluído.
     * Isso preserva histórico da comissão devida em pagamentos passados.
     *
     * @param dadosAfiliado null pra REMOVER vínculo, senão preenche snapshot
     */
    @org.springframework.transaction.annotation.Transactional(transactionManager = "mainTransactionManager")
    public Map<String, Object> vincularAfiliado(Long restauranteId,
                                                Map<String, Object> dadosAfiliado) {
        RestauranteMain r = restauranteRepo.findById(restauranteId)
                .orElseThrow(() -> new NotFoundException("Restaurante não encontrado"));

        String antigoCodigo = r.getAfiliadoCodigo();

        if (dadosAfiliado == null || dadosAfiliado.isEmpty()) {
            // DESVINCULAR — zera todos os campos snap
            r.setAfiliadoCodigo(null);
            r.setAfiliadoIdSnap(null);
            r.setAfiliadoNomeSnap(null);
            r.setAfiliadoEmailSnap(null);
            r.setAfiliadoComissaoSnap(null);
            r.setAfiliadoVinculadoEm(null);
            restauranteRepo.save(r);
            log.warn("[Afiliado] restaurante {} DESVINCULADO (era {})", restauranteId, antigoCodigo);
            return Map.of(
                "ok", true,
                "restauranteId", restauranteId,
                "vinculado", false,
                "codigoAnterior", antigoCodigo == null ? "" : antigoCodigo
            );
        }

        String codigo = String.valueOf(dadosAfiliado.get("codigo"));
        if (codigo == null || codigo.isBlank() || "null".equals(codigo)) {
            throw new IllegalArgumentException("codigo do afiliado é obrigatório");
        }
        r.setAfiliadoCodigo(codigo.trim());
        r.setAfiliadoIdSnap(parseLong(dadosAfiliado.get("id")));
        r.setAfiliadoNomeSnap(nullSafe(dadosAfiliado.get("nome")));
        r.setAfiliadoEmailSnap(nullSafe(dadosAfiliado.get("email")));
        Object comissaoRaw = dadosAfiliado.get("comissao");
        if (comissaoRaw != null) {
            try {
                r.setAfiliadoComissaoSnap(new java.math.BigDecimal(comissaoRaw.toString()));
            } catch (NumberFormatException ignore) {}
        }
        r.setAfiliadoVinculadoEm(java.time.LocalDateTime.now());
        restauranteRepo.save(r);
        log.warn("[Afiliado] restaurante {} vinculado a codigo={} nome={} (antes: {})",
                restauranteId, codigo, r.getAfiliadoNomeSnap(), antigoCodigo);
        return Map.of(
            "ok", true,
            "restauranteId", restauranteId,
            "vinculado", true,
            "codigoAnterior", antigoCodigo == null ? "" : antigoCodigo,
            "codigoNovo", codigo,
            "nome", r.getAfiliadoNomeSnap() == null ? "" : r.getAfiliadoNomeSnap()
        );
    }

    private static Long parseLong(Object o) {
        if (o == null) return null;
        try { return Long.valueOf(o.toString()); } catch (Exception e) { return null; }
    }
    private static String nullSafe(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    public Map<String, Integer> apagarDefinitivamente(Long id) {
        // Valida que existe antes (mensagem clara em vez de erro silencioso)
        RestauranteMain r = restauranteRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Restaurante não encontrado"));
        log.warn("[Restaurante] APAGAR DEFINITIVO id={} nome={} status={}",
                id, r.getNome(), r.getStatus());
        return writer.apagarRestauranteCompletamente(id);
    }
}
