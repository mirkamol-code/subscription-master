package com.mirkamolcode.notification;

import com.mirkamolcode.entity.GmailConnection;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.integration.gmail.GmailClient;
import com.mirkamolcode.model.NotificationSeverity;
import com.mirkamolcode.model.NotificationType;
import com.mirkamolcode.repository.GmailConnectionRepository;
import com.mirkamolcode.service.GmailAccessor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailChannelTest {
    private static final long USER_ID = 7L;
    private static final String FRONTEND_BASE_URL = "https://subscriptionmaster.app";

    private final GmailConnectionRepository connections = mock(GmailConnectionRepository.class);
    private final GmailClient gmail = mock(GmailClient.class);
    private final GmailAccessor gmailAccessor = mock(GmailAccessor.class);
    private final User user = mock(User.class);
    private EmailChannel underTest;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(user.getId()).thenReturn(USER_ID);
        GmailConnection connection = new GmailConnection(user, "person@gmail.com", "encrypted", "gmail.readonly gmail.send");
        when(connections.findByUserId(USER_ID)).thenReturn(Optional.of(connection));
        when(gmailAccessor.callWithFreshToken(eq(USER_ID), any())).thenAnswer(invocation ->
                invocation.<Function<String, Object>>getArgument(1).apply("access-token"));
        underTest = new EmailChannel(connections, gmail, gmailAccessor, FRONTEND_BASE_URL);
    }

    @Test
    void send_shouldShowBrandLogoAndBrandButton_whenReminderIsEmailed() {
        // given
        NotificationMessage message = new NotificationMessage(NotificationType.PAYMENT_DUE, NotificationSeverity.INFO,
                1L, "Netflix renews tomorrow", "15.49 USD", FRONTEND_BASE_URL + "/subscriptions/1");

        // when
        underTest.send(user, message);

        // then
        String html = sentHtml();
        assertThat(html).contains("<img src=\"https://subscriptionmaster.app/email-logo.png\"");
        assertThat(html).contains("background:#7c3aed");
        assertThat(html).doesNotContain("#2563eb");
    }

    private String sentHtml() {
        ArgumentCaptor<String> raw = ArgumentCaptor.forClass(String.class);
        verify(gmail).sendMessage(eq("access-token"), raw.capture());
        String mime = new String(Base64.getUrlDecoder().decode(raw.getValue()), StandardCharsets.UTF_8);
        String body = mime.substring(mime.indexOf("\r\n\r\n") + 4).replace("\r\n", "");
        return new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8);
    }
}
