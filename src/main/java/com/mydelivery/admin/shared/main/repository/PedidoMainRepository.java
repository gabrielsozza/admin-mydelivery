package com.mydelivery.admin.shared.main.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mydelivery.admin.shared.main.entity.PedidoMain;

/**
 * Repository de leitura de pedidos do main DB.
 *
 * Todas as queries são agregadas — nunca lista pedidos individuais, porque a
 * tabela pode ter milhões de linhas. Cabe filtros de data e exclusão de CANCELADO.
 *
 * NOTA: status CANCELADO é hardcoded como literal porque é o valor real no main —
 * se o main mudar o enum, atualize aqui também.
 */
public interface PedidoMainRepository extends JpaRepository<PedidoMain, Long> {

    /** Soma valor_total dos pedidos NÃO cancelados em um intervalo. */
    @Query("""
        SELECT COALESCE(SUM(p.valorTotal), 0) FROM PedidoMain p
        WHERE p.criadoEm >= :inicio AND p.criadoEm < :fimExcl
          AND (p.status IS NULL OR p.status <> 'CANCELADO')
        """)
    BigDecimal somaGmvEntre(@Param("inicio") LocalDateTime inicio,
                            @Param("fimExcl") LocalDateTime fimExcl);

    /** Quantidade de pedidos NÃO cancelados em um intervalo. */
    @Query("""
        SELECT COUNT(p) FROM PedidoMain p
        WHERE p.criadoEm >= :inicio AND p.criadoEm < :fimExcl
          AND (p.status IS NULL OR p.status <> 'CANCELADO')
        """)
    long countPedidosEntre(@Param("inicio") LocalDateTime inicio,
                           @Param("fimExcl") LocalDateTime fimExcl);

    /**
     * Série mensal: retorna [ano, mes, somaValor, contagem] pra cada mês com pedidos.
     * Ordenado cronologicamente.
     */
    @Query("""
        SELECT FUNCTION('YEAR', p.criadoEm), FUNCTION('MONTH', p.criadoEm),
               COALESCE(SUM(p.valorTotal), 0), COUNT(p)
          FROM PedidoMain p
         WHERE p.criadoEm >= :inicio
           AND (p.status IS NULL OR p.status <> 'CANCELADO')
         GROUP BY FUNCTION('YEAR', p.criadoEm), FUNCTION('MONTH', p.criadoEm)
         ORDER BY FUNCTION('YEAR', p.criadoEm), FUNCTION('MONTH', p.criadoEm)
        """)
    List<Object[]> serieMensal(@Param("inicio") LocalDateTime inicio);

    /**
     * Top N restaurantes por GMV em um período.
     * Retorna [restauranteId, somaValor, contagem].
     */
    @Query("""
        SELECT p.restauranteId, COALESCE(SUM(p.valorTotal), 0), COUNT(p)
          FROM PedidoMain p
         WHERE p.criadoEm >= :inicio AND p.criadoEm < :fimExcl
           AND (p.status IS NULL OR p.status <> 'CANCELADO')
         GROUP BY p.restauranteId
         ORDER BY SUM(p.valorTotal) DESC
        """)
    List<Object[]> topRestaurantesPorGmv(@Param("inicio") LocalDateTime inicio,
                                         @Param("fimExcl") LocalDateTime fimExcl,
                                         org.springframework.data.domain.Pageable pageable);

    /** GMV total de UM restaurante num período. */
    @Query("""
        SELECT COALESCE(SUM(p.valorTotal), 0) FROM PedidoMain p
         WHERE p.restauranteId = :restauranteId
           AND p.criadoEm >= :inicio AND p.criadoEm < :fimExcl
           AND (p.status IS NULL OR p.status <> 'CANCELADO')
        """)
    BigDecimal gmvDoRestauranteEntre(@Param("restauranteId") Long restauranteId,
                                     @Param("inicio") LocalDateTime inicio,
                                     @Param("fimExcl") LocalDateTime fimExcl);
}
