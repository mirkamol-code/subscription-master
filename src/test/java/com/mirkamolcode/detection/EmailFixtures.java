package com.mirkamolcode.detection;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.integration.gmail.GmailMessageMapper;
import com.mirkamolcode.integration.gmail.MessageHeaders;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.JacksonException;

public final class EmailFixtures {
    private static final ObjectMapper JSON = new JsonMapper();

    private EmailFixtures() {
    }

    public static GmailMessage load(String fixture) {
        return GmailMessageMapper.toMessage(loadJson(fixture));
    }

    public static GmailMessage authenticated(GmailMessage message) {
        String domain = message.senderDomain();
        MessageHeaders headers = message.headers().withAuthenticationResults(
                "mx.google.com; dkim=pass header.i=@" + domain + "; spf=pass smtp.mailfrom=bounces@" + domain
                        + "; dmarc=pass header.from=" + domain);
        return new GmailMessage(message.id(), message.threadId(), message.receivedAt(), message.from(), message.subject(),
                message.htmlBody(), message.textBody(), message.attachments(), headers);
    }

    public static tools.jackson.databind.JsonNode loadJson(String fixture) {
        try (InputStream in = EmailFixtures.class.getResourceAsStream("/emails/" + fixture)) {
            return JSON.readTree(in);
        } catch (IOException | JacksonException e) {
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
