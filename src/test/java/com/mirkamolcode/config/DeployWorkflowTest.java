package com.mirkamolcode.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;

class DeployWorkflowTest {
    private static final Pattern REQUIRED_BY_COMPOSE = Pattern.compile("\\$\\{([A-Z0-9_]+):\\?");
    private static final Pattern NAME_LOOP = Pattern.compile("for name in ([A-Z0-9_ ]+);");

    @Test
    void deploy_shouldReadEveryVariableComposeRequiresFromSecrets() {
        // given
        Set<String> required = requiredByCompose();

        // when
        Map<String, Object> environment = deployJobEnvironment();

        // then
        assertThat(required).isNotEmpty();
        required.forEach(name ->
                assertThat(String.valueOf(environment.get(name))).as(name).isEqualTo("${{ secrets." + name + " }}"));
    }

    @Test
    void deploy_shouldFailEarly_whenSecretComposeRequiresIsMissing() {
        // when
        Set<String> checked = namesLoopedOver("Check Required Secrets");

        // then
        assertThat(checked).containsAll(requiredByCompose());
    }

    @Test
    void deploy_shouldWriteEveryVariableComposeRequiresToServerEnv() {
        // when
        Set<String> written = namesLoopedOver("SSH To EC2 and Deploy");

        // then
        assertThat(written).containsAll(requiredByCompose());
    }

    private static Set<String> requiredByCompose() {
        Set<String> names = new TreeSet<>();
        Matcher placeholder = REQUIRED_BY_COMPOSE.matcher(read("docker-compose.yml"));
        while (placeholder.find())
            names.add(placeholder.group(1));
        return names;
    }

    private static Set<String> namesLoopedOver(String stepName) {
        Matcher loop = NAME_LOOP.matcher(stepScript(stepName));
        assertThat(loop.find()).as("name loop in " + stepName).isTrue();
        return new TreeSet<>(List.of(loop.group(1).trim().split("\\s+")));
    }

    @SuppressWarnings("unchecked")
    private static String stepScript(String stepName) {
        List<Map<String, Object>> steps = (List<Map<String, Object>>) deployJob().get("steps");
        return steps.stream()
                .filter(step -> stepName.equals(step.get("name")))
                .map(step -> String.valueOf(step.get("run")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing step " + stepName));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deployJobEnvironment() {
        return (Map<String, Object>) deployJob().get("env");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deployJob() {
        Map<String, Object> workflow = new Yaml().load(read(".github/workflows/deploy.yml"));
        return (Map<String, Object>) ((Map<String, Object>) workflow.get("jobs")).get("build-and-deploy");
    }

    private static String read(String path) {
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            return new String(in.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
