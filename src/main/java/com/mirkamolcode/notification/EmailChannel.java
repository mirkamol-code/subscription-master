package com.mirkamolcode.notification;

import com.mirkamolcode.entity.GmailConnection;
import com.mirkamolcode.entity.NotificationSettings;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.exception.GmailDisconnectedException;
import com.mirkamolcode.integration.gmail.GmailClient;
import com.mirkamolcode.integration.gmail.GmailRawMessage;
import com.mirkamolcode.integration.gmail.GmailScopeMissingException;
import com.mirkamolcode.model.NotificationChannelType;
import com.mirkamolcode.model.NotificationSeverity;
import com.mirkamolcode.repository.GmailConnectionRepository;
import com.mirkamolcode.service.GmailAccessor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class EmailChannel implements NotificationChannel {
    private static final Logger log = LoggerFactory.getLogger(EmailChannel.class);
    private static final String URGENT_ACCENT = "#b91c1c";
    private static final String INFO_ACCENT = "#111827";

    private final GmailConnectionRepository connections;
    private final GmailClient gmail;
    private final GmailAccessor gmailAccessor;
    private final String template;

    public EmailChannel(GmailConnectionRepository connections, GmailClient gmail, GmailAccessor gmailAccessor) {
        this.connections = connections;
        this.gmail = gmail;
        this.gmailAccessor = gmailAccessor;
        this.template = loadTemplate();
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    public boolean isEnabled(NotificationSettings settings) {
        return settings.isEmailEnabled();
    }

    @Override
    public void send(User user, NotificationMessage message) {
        GmailConnection connection = connections.findByUserId(user.getId()).filter(GmailConnection::isConnected).orElse(null);
        if (connection == null) {
            log.debug("Skipping email notification for user {}: Gmail is not connected", user.getId());
            return;
        }
        String raw = GmailRawMessage.build(connection.getGmailAddress(), message.title(), renderHtml(message));
        try {
            gmailAccessor.callWithFreshToken(user.getId(), accessToken -> {
                gmail.sendMessage(accessToken, raw);
                return null;
            });
        } catch (GmailScopeMissingException e) {
            log.info("Skipping email notification for user {}: gmail.send was not granted", user.getId());
        } catch (GmailDisconnectedException e) {
            log.info("Skipping email notification for user {}: Gmail access was revoked", user.getId());
        }
    }

    private String renderHtml(NotificationMessage message) {
        return template
                .replace("{{accent}}", message.severity() == NotificationSeverity.URGENT ? URGENT_ACCENT : INFO_ACCENT)
                .replace("{{title}}", HtmlUtils.htmlEscape(message.title()))
                .replace("{{body}}", HtmlUtils.htmlEscape(message.body()))
                .replace("{{link}}", HtmlUtils.htmlEscape(message.link()));
    }

    private static String loadTemplate() {
        try (InputStream in = new ClassPathResource("templates/mail/notification.html").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
