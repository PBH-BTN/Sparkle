package com.ghostchu.btn.sparkle.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
@EnableJpaRepositories(basePackages = "com.ghostchu.btn.sparkle.module.analyse",
        entityManagerFactoryRef = "analyseEntityManagerFactory",
        transactionManagerRef = "analyseTransactionManager")
public class AnalyseDatabaseConfig {

    @Bean("analyseDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.analyse")
    public DataSource analyseDatasource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "analyseEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(EntityManagerFactoryBuilder builder, @Qualifier("analyseDataSource") DataSource dataSource) {
        return builder.dataSource(dataSource).packages("com.ghostchu.btn.sparkle.module.analyse").persistenceUnit("customer").build();
    }

    @Bean(name = "analyseTransactionManager")
    public PlatformTransactionManager customerTransactionManager(@Qualifier("analyseEntityManagerFactory") EntityManagerFactory customerEntityManagerFactory) {
        return new JpaTransactionManager(customerEntityManagerFactory);
    }

}
