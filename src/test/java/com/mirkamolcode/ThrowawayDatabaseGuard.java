package com.mirkamolcode;

import com.zaxxer.hikari.HikariDataSource;

import java.net.URI;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class ThrowawayDatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final String POSTGRES_PREFIX = "jdbc:postgresql://";
    private static final int POSTGRES_PORT = 5432;

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        context.getBeanFactory().addBeanPostProcessor(new DataSourceCheck());
    }

    static void requireThrowaway(String jdbcUrl) {
        SharedPostgresContainer container = SharedPostgresContainer.running();
        String allowed = container.getHost() + ":" + container.getMappedPort(POSTGRES_PORT);
        String target = hostAndPort(jdbcUrl);
        if (!allowed.equals(target))
            throw new IllegalStateException("Tests may only use the throwaway Testcontainers database at " + allowed
                    + "; refusing " + target);
    }

    private static String hostAndPort(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith(POSTGRES_PREFIX))
            return "an unknown database";
        URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        return uri.getHost() + ":" + (uri.getPort() < 0 ? POSTGRES_PORT : uri.getPort());
    }

    private static final class DataSourceCheck implements BeanPostProcessor {
        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (bean instanceof HikariDataSource hikari)
                requireThrowaway(hikari.getJdbcUrl());
            else if (bean instanceof DataSource)
                throw new IllegalStateException("Tests may only use the throwaway database; cannot check DataSource " + beanName);
            return bean;
        }
    }
}
