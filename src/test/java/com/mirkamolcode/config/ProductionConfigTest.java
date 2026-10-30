package com.mirkamolcode.config;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionConfigTest {

    @Test
    void prodProfile_shouldOnlyValidateSchema_andLeaveChangesToFlyway() {
        // when
        PropertySource<?> prod = load("application-prod.yml");

        // then
        assertThat(prod.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(String.valueOf(prod.getProperty("spring.flyway.enabled"))).isEqualTo("true");
    }

    @Test
    void prodProfile_shouldNeverSeedDemoDataOrLogSql() {
        // when
        PropertySource<?> prod = load("application-prod.yml");

        // then
        assertThat(String.valueOf(prod.getProperty("app.seed.enabled"))).isEqualTo("false");
        assertThat(String.valueOf(prod.getProperty("spring.jpa.properties.hibernate.show_sql"))).isEqualTo("false");
    }

    @Test
    void prodProfile_shouldRequireSecretsAndDatabaseSettings_withoutFallbackValues() {
        // given
        Map<String, String> required = Map.of(
                "spring.datasource.url", "SPRING_DATASOURCE_URL",
                "spring.datasource.username", "SPRING_DATASOURCE_USERNAME",
                "spring.datasource.password", "SPRING_DATASOURCE_PASSWORD",
                "app.jwt.secret", "JWT_SECRET",
                "app.security.token-encryption-key", "TOKEN_ENCRYPTION_KEY",
                "app.frontend-base-url", "FRONTEND_BASE_URL",
                "spring.security.oauth2.client.registration.google.client-id", "GOOGLE_CLIENT_ID",
                "spring.security.oauth2.client.registration.google.client-secret", "GOOGLE_CLIENT_SECRET",
                "spring.security.oauth2.client.registration.google.redirect-uri", "GOOGLE_REDIRECT_URI");

        // when
        PropertySource<?> prod = load("application-prod.yml");

        // then
        required.forEach((property, variable) ->
                assertThat(String.valueOf(prod.getProperty(property))).as(property).isEqualTo("${" + variable + "}"));
    }

    @Test
    void baseConfig_shouldNeverLetHibernateCreateOrAlterTables() {
        // when
        PropertySource<?> base = load("application.yml");

        // then
        assertThat(base.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    static PropertySource<?> load(String resource) {
        try {
            return new YamlPropertySourceLoader().load(resource, new ClassPathResource(resource)).getFirst();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
