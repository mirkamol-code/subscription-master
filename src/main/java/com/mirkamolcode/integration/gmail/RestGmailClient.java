package com.mirkamolcode.integration.gmail;

import com.fasterxml.jackson.databind.JsonNode;
import com.mirkamolcode.exception.GoogleApiUnavailableException;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

@Component
public class RestGmailClient implements GmailClient {
    private static final int PAGE_SIZE = 500;
    private static final int MAX_ATTEMPTS = 4;
    private static final Set<String> RATE_LIMIT_REASONS = Set.of("rateLimitExceeded", "userRateLimitExceeded");

    private final RestClient client;
    private final Retry retry;

    public RestGmailClient(RestClient.Builder builder,
                           RetryRegistry retries,
                           @Value("${app.gmail.api-base-url}") String baseUrl,
                           @Value("${app.gmail.connect-timeout}") Duration connectTimeout,
                           @Value("${app.gmail.read-timeout}") Duration readTimeout,
                           @Value("${app.gmail.retry-initial-backoff}") Duration retryInitialBackoff) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.client = builder.baseUrl(baseUrl + "/gmail/v1/users/me").requestFactory(requestFactory).build();
        this.retry = retries.retry("gmail", buildRetryConfig(retryInitialBackoff));
    }

    private static RetryConfig buildRetryConfig(Duration initialBackoff) {
        return RetryConfig.custom()
                .maxAttempts(MAX_ATTEMPTS)
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(initialBackoff, 2.0, 0.5))
                .retryExceptions(GmailTransientException.class)
                .build();
    }

    @Override
    public GmailMessagePage listMessageIds(String accessToken, String query, String pageToken) {
        JsonNode body = fetch(accessToken, uri -> withPageToken(uri.path("/messages")
                .queryParam("q", query)
                .queryParam("maxResults", PAGE_SIZE), pageToken).build());
        List<String> ids = new ArrayList<>();
        body.path("messages").forEach(message -> ids.add(message.path("id").asText()));
        return new GmailMessagePage(ids, readTextOrNull(body, "nextPageToken"));
    }

    @Override
    public GmailMessage getMessage(String accessToken, String messageId) {
        return GmailMessageMapper.toMessage(fetch(accessToken, uri -> uri.path("/messages/{id}")
                .queryParam("format", "full")
                .build(messageId)));
    }

    @Override
    public GmailMessage getMessageHeaders(String accessToken, String messageId) {
        return GmailMessageMapper.toMessage(fetch(accessToken, uri -> uri.path("/messages/{id}")
                .queryParam("format", "metadata")
                .queryParam("metadataHeaders", "From", "Subject")
                .build(messageId)));
    }

    @Override
    public GmailHistoryPage listHistory(String accessToken, String startHistoryId, String pageToken) {
        JsonNode body;
        try {
            body = fetch(accessToken, uri -> withPageToken(uri.path("/history")
                    .queryParam("startHistoryId", startHistoryId)
                    .queryParam("historyTypes", "messageAdded"), pageToken).build());
        } catch (GmailNotFoundException e) {
            throw new GmailHistoryExpiredException(startHistoryId);
        }
        List<String> added = new ArrayList<>();
        body.path("history").forEach(entry -> entry.path("messagesAdded")
                .forEach(item -> added.add(item.path("message").path("id").asText())));
        return new GmailHistoryPage(added, readTextOrNull(body, "nextPageToken"), readTextOrNull(body, "historyId"));
    }

    @Override
    public String fetchCurrentHistoryId(String accessToken) {
        return fetch(accessToken, uri -> uri.path("/profile").build()).path("historyId").asText();
    }

    private JsonNode fetch(String accessToken, Function<UriBuilder, URI> uri) {
        Supplier<JsonNode> request = () -> {
            try {
                return client.get()
                        .uri(uri)
                        .headers(headers -> headers.setBearerAuth(accessToken))
                        .exchange((req, response) -> {
                            JsonNode body = response.bodyTo(JsonNode.class);
                            if (response.getStatusCode().is2xxSuccessful())
                                return body;
                            throw toException(response.getStatusCode(), body);
                        });
            } catch (RestClientException e) {
                throw new GmailTransientException("Gmail API unreachable", e);
            }
        };
        return retry.executeSupplier(request);
    }

    private static RuntimeException toException(HttpStatusCode status, JsonNode body) {
        String reason = body == null ? "" : body.path("error").path("errors").path(0).path("reason").asText("");
        String message = "Gmail API failed with " + status.value() + " " + reason;
        if (status.value() == 429 || (status.value() == 403 && RATE_LIMIT_REASONS.contains(reason)))
            return new GmailTransientException(message);
        if (status.value() == 401 || status.value() == 403)
            return new GmailUnauthorizedException(message);
        if (status.value() == 404)
            return new GmailNotFoundException(message);
        if (status.is5xxServerError())
            return new GmailTransientException(message);
        return new GoogleApiUnavailableException(message);
    }

    private static UriBuilder withPageToken(UriBuilder uri, String pageToken) {
        return pageToken == null ? uri : uri.queryParam("pageToken", pageToken);
    }

    private static String readTextOrNull(JsonNode body, String field) {
        return body.hasNonNull(field) ? body.get(field).asText() : null;
    }
}
