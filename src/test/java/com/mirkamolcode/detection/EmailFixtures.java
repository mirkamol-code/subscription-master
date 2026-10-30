package com.mirkamolcode.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.integration.gmail.GmailMessageMapper;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

public final class EmailFixtures {
    private static final ObjectMapper JSON = new ObjectMapper();

    private EmailFixtures() {
    }

    public static GmailMessage load(String fixture) {
        return GmailMessageMapper.toMessage(loadJson(fixture));
    }

    public static com.fasterxml.jackson.databind.JsonNode loadJson(String fixture) {
        try (InputStream in = EmailFixtures.class.getResourceAsStream("/emails/" + fixture)) {
            return JSON.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static DetectionProperties detectionProperties() {
        try {
            StandardEnvironment environment = new StandardEnvironment();
            new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                    .forEach(source -> environment.getPropertySources().addLast(source));
            return Binder.get(environment).bind("app.gmail.detection", DetectionProperties.class).get();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
