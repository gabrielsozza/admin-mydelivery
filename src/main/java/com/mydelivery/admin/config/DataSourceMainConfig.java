package com.mydelivery.admin.config;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * DataSource SECUNDÁRIO — banco do MyDelivery principal (somente leitura).
 *
 * Aqui o admin LÊ dados de restaurantes, pedidos, pagamentos, etc — coisas que
 * vivem no banco do projeto principal. NUNCA escreve.
 *
 * As entidades espelho ficam em {@code com.mydelivery.admin.shared.main.entity}
 * e os repositories em {@code com.mydelivery.admin.shared.main.repository}.
 * Cada entidade replica APENAS os campos que o admin precisa ler (não precisa
 * copiar tudo do modelo original — espelhar só o necessário).
 *
 * IMPORTANTE: ddl-auto=none aqui, NUNCA "update". O admin não pode criar/alterar
 * tabelas no banco dos restaurantes.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.mydelivery.admin.shared.main.repository",
        entityManagerFactoryRef = "mainEntityManagerFactory",
        transactionManagerRef = "mainTransactionManager"
)
public class DataSourceMainConfig {

    @Bean(name = "mainDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.main")
    public DataSource mainDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "mainEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean mainEntityManagerFactory(
            @Qualifier("mainDataSource") DataSource dataSource) {

        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.mydelivery.admin.shared.main.entity");
        em.setPersistenceUnitName("main");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);

        Map<String, Object> properties = new HashMap<>();
        // CRÍTICO: never altera schema do banco principal!
        properties.put("hibernate.hbm2ddl.auto", "none");
        properties.put("hibernate.show_sql", "false");
        em.setJpaPropertyMap(properties);

        return em;
    }

    @Bean(name = "mainTransactionManager")
    public PlatformTransactionManager mainTransactionManager(
            @Qualifier("mainEntityManagerFactory") LocalContainerEntityManagerFactoryBean emf) {
        JpaTransactionManager tm = new JpaTransactionManager();
        tm.setEntityManagerFactory(emf.getObject());
        return tm;
    }

    /**
     * JdbcTemplate exclusivo pro main DS.
     *
     * USADO APENAS pelo {@code MainDbWriter} pra escritas escopadas em colunas
     * específicas. Não é injetado em mais nenhum lugar — pra qualquer leitura,
     * use os repositories JPA das mirrors.
     */
    @Bean(name = "mainJdbcTemplate")
    public JdbcTemplate mainJdbcTemplate(@Qualifier("mainDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
