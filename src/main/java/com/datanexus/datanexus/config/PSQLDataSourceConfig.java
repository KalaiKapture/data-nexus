package com.datanexus.datanexus.config;

import com.zaxxer.hikari.HikariDataSource;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.orm.hibernate5.LocalSessionFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
public class PSQLDataSourceConfig {

    @Value("${spring.datasource.psql.url}")
    private String url;

    @Value("${spring.datasource.psql.username}")
    private String username;

    @Value("${spring.datasource.psql.password}")
    private String password;

    @Value("${spring.datasource.psql.driver-class-name}")
    private String driverClassName;

    @Value("${spring.datasource.psql.hikari.maximum-pool-size:10}")
    private int maxPoolSize;

    @Value("${spring.datasource.psql.hikari.minimum-idle:2}")
    private int minIdle;

    @Value("${spring.datasource.psql.hikari.idle-timeout:30000}")
    private long idleTimeout;

    @Value("${spring.datasource.psql.hikari.connection-timeout:20000}")
    private long connectionTimeout;

    @Value("${spring.jpa.hibernate.ddl-auto:update}")
    private String ddlAuto;

    @Bean(name = "psqlMainDataSource")
    @Primary
    public DataSource psqlMainDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName(driverClassName);
        ds.setMaximumPoolSize(maxPoolSize);
        ds.setMinimumIdle(minIdle);
        ds.setIdleTimeout(idleTimeout);
        ds.setConnectionTimeout(connectionTimeout);
        return ds;
    }

    @Bean(name = "psqlMainSessionFactory")
    @Primary
    public LocalSessionFactoryBean psqlMainSessionFactory(
            @Qualifier("psqlMainDataSource") DataSource dataSource) {
        LocalSessionFactoryBean sessionFactory = new LocalSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);
        sessionFactory.setPackagesToScan("com.datanexus.datanexus.entity");

        Properties hibernateProperties = new Properties();
        hibernateProperties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        hibernateProperties.put("hibernate.hbm2ddl.auto", ddlAuto);
        hibernateProperties.put("hibernate.show_sql", "false");
        hibernateProperties.put("hibernate.format_sql", "true");
        sessionFactory.setHibernateProperties(hibernateProperties);

        return sessionFactory;
    }

    @Bean(name = "psqlMainJdbcTemplate")
    @Primary
    public JdbcTemplate psqlMainJdbcTemplate(
            @Qualifier("psqlMainDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "psqlTransactionManager")
    @Primary
    public PlatformTransactionManager psqlTransactionManager(
            @Qualifier("psqlMainSessionFactory") SessionFactory sessionFactory) {
        HibernateTransactionManager txManager = new HibernateTransactionManager();
        txManager.setSessionFactory(sessionFactory);
        return txManager;
    }
}
