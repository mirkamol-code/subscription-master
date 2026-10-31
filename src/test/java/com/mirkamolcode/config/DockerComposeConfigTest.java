package com.mirkamolcode.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;

class DockerComposeConfigTest {
    private static final Pattern REQUIRED_PLACEHOLDER = Pattern.compile("\\$\\{([A-Z0-9_]+)}");
    private static final List<String> SECRETS_AND_DATABASE = List.of("JWT_SECRET", "TOKEN_ENCRYPTION_KEY", "GOOGLE_CLIENT_ID",
            "GOOGLE_CLIENT_SECRET", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD");

    @Test
    void app_shouldRunWithProductionProfile() {
        // when / then
        assertThat(appEnvironment()).containsEntry("SPRING_PROFILES_ACTIVE", "prod");
    }

    @Test
    void app_shouldReceiveEveryPlaceholderTheConfigurationRequires() {
        // given
        Set<String> required = new TreeSet<>();
        required.addAll(placeholdersWithoutDefault(ProductionConfigTest.load("application.yml")));
        required.addAll(placeholdersWithoutDefault(ProductionConfigTest.load("application-prod.yml")));

        // when / then
        assertThat(required).isNotEmpty();
        assertThat(appEnvironment().keySet()).containsAll(required);
    }

    @Test
    void app_shouldFailToStart_whenSecretOrDatabaseCredentialIsMissing() {
        // when
        Map<String, String> environment = appEnvironment();

        // then
        SECRETS_AND_DATABASE.forEach(name ->
                assertThat(environment.get(name)).as(name).contains(":?"));
    }

    @Test
    void app_shouldBePublishedOnLocalhostOnly() {
        // when
        Map<String, Object> app = service("app");

        // then
        assertThat((List<?>) app.get("ports")).singleElement().asString().startsWith("127.0.0.1:");
    }

    @Test
    void database_shouldBePublishedOnLocalhostOnly() {
        // when
        Map<String, Object> db = service("db");

        // then
        assertThat((List<?>) db.get("ports")).singleElement().asString().startsWith("127.0.0.1:");
    }

    @Test
    void bundledNginx_shouldStartOnlyWhenItsProfileIsRequested() {
        // when
        Map<String, Object> nginx = service("nginx");

        // then
        assertThat(String.valueOf(nginx.get("profiles"))).isEqualTo("[bundled-nginx]");
    }

    @Test
    void localDatabase_shouldStartOnlyWhenItsProfileIsRequested() {
        // when
        Map<String, Object> db = service("db");

        // then
        assertThat(String.valueOf(db.get("profiles"))).isEqualTo("[local-db]");
    }

    @Test
    @SuppressWarnings("unchecked")
    void app_shouldStartWithoutLocalDatabase_whenItUsesAnExternalOne() {
        // when
        Map<String, Object> dependsOn = (Map<String, Object>) service("app").get("depends_on");

        // then
        assertThat((Map<String, Object>) dependsOn.get("db"))
                .containsEntry("condition", "service_healthy")
                .containsEntry("required", false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void database_shouldFailToStart_whenCredentialIsMissing() {
        // when
        Map<String, Object> environment = (Map<String, Object>) service("db").get("environment");

        // then
        List.of("POSTGRES_USER", "POSTGRES_PASSWORD").forEach(name ->
                assertThat(String.valueOf(environment.get(name))).as(name).contains(":?"));
    }

    private static Set<String> placeholdersWithoutDefault(PropertySource<?> source) {
        Set<String> names = new TreeSet<>();
        for (String property : ((EnumerablePropertySource<?>) source).getPropertyNames()) {
            Matcher placeholder = REQUIRED_PLACEHOLDER.matcher(String.valueOf(source.getProperty(property)));
            while (placeholder.find())
                names.add(placeholder.group(1));
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> appEnvironment() {
        Map<String, Object> environment = (Map<String, Object>) service("app").get("environment");
        Map<String, String> values = new LinkedHashMap<>();
        environment.forEach((key, value) -> values.put(key, String.valueOf(value)));
        return values;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> service(String name) {
        try (InputStream in = Files.newInputStream(Path.of("docker-compose.yml"))) {
            Map<String, Object> compose = new Yaml().load(in);
            return (Map<String, Object>) ((Map<String, Object>) compose.get("services")).get(name);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
