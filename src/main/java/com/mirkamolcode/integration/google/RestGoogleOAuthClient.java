package com.mirkamolcode.integration.google;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mirkamolcode.exception.GoogleApiUnavailableException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RestGoogleOAuthClient implements GoogleOAuthClient {
    private static final String INVALID_GRANT = "invalid_grant";
    private static final int ALREADY_REVOKED = 400;
    private static final int TOO_MANY_REQUESTS = 429;

    private final RestClient client;
    private final Retry retry;
    private final String clientId;
    private final String clientSecret;

    public RestGoogleOAuthClient(RestClient.Builder builder,
                                 RetryRegistry retries,
                                 @Value("${app.google.oauth-base-url}") String baseUrl,
                                 @Value("${spring.security.oauth2.client.registration.google.client-id}") String clientId,
                                 @Value("${spring.security.oauth2.client.registration.google.client-secret}") String clientSecret,
                                 @Value("${app.google.connect-timeout}") Duration connectTimeout,
                                 @Value("${app.google.read-timeout}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.client = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.retry = retries.retry("googleOAuth");
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public GoogleTokens refreshAccessToken(String refreshToken) {
        return retry.executeSupplier(() -> requestAccessToken(refreshToken));
    }

    @Override
    public boolean revoke(String token) {
        try {
            return retry.executeSupplier(() -> requestRevocation(token));
        } catch (GoogleApiUnavailableException e) {
            return false;
        }
    }

    private boolean requestRevocation(String token) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        try {
            return client.post().uri("/revoke")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == TOO_MANY_REQUESTS || response.getStatusCode().is5xxServerError())
                            throw new GoogleApiUnavailableException("Google revoke endpoint failed with " + status);
                        return response.getStatusCode().is2xxSuccessful() || status == ALREADY_REVOKED;
                    });
        } catch (RestClientException e) {
            throw new GoogleApiUnavailableException("Google revoke endpoint unreachable", e);
        }
    }

    private GoogleTokens requestAccessToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        try {
            return client.post().uri("/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().is2xxSuccessful()) {
                            TokenBody body = response.bodyTo(TokenBody.class);
                            if (body == null || body.accessToken() == null)
                                throw new GoogleApiUnavailableException("Google returned no access token");
                            return new GoogleTokens(body.accessToken(), Duration.ofSeconds(body.expiresIn()));
                        }
                        ErrorBody error = readError(response);
                        if (INVALID_GRANT.equals(error.error()))
                            throw new InvalidGrantException(error.description());
                        throw new GoogleApiUnavailableException("Google token endpoint failed with " + response.getStatusCode().value() + " " + error.error());
                    });
        } catch (RestClientException e) {
            throw new GoogleApiUnavailableException("Google token endpoint unreachable", e);
        }
    }

    private static ErrorBody readError(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        try {
            ErrorBody body = response.bodyTo(ErrorBody.class);
            return body != null ? body : ErrorBody.UNKNOWN;
        } catch (RestClientException e) {
            return ErrorBody.UNKNOWN;
        }
    }

    private record TokenBody(@JsonProperty("access_token") String accessToken,
                             @JsonProperty("expires_in") long expiresIn) {
    }

    private record ErrorBody(@JsonProperty("error") String error,
                             @JsonProperty("error_description") String description) {
        private static final ErrorBody UNKNOWN = new ErrorBody("unknown", "No error body");
    }
}
