package com.mirkamolcode;

import com.mirkamolcode.dto.AuthDtos.LoginRequest;
import com.mirkamolcode.dto.AuthDtos.RegisterRequest;
import com.mirkamolcode.dto.AuthDtos.TokenResponse;
import com.mirkamolcode.dto.request.SubscriptionRequest;
import com.mirkamolcode.dto.response.SubscriptionResponse;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.model.Role;
import com.mirkamolcode.repository.ExchangeRateRepository;
import com.mirkamolcode.repository.PaymentHistoryRepository;
import com.mirkamolcode.repository.RefreshTokenRepository;
import com.mirkamolcode.repository.SubscriptionRepository;
import com.mirkamolcode.repository.UserRepository;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractTestConfig {
    protected static final String API = "";

    @Container
    @ServiceConnection
    private static final SharedPostgresContainer POSTGRES =
            SharedPostgresContainer.getInstance();

    @Autowired
    protected WebTestClient webTestClient;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected SubscriptionRepository subscriptionRepository;
    @Autowired
    protected PaymentHistoryRepository paymentHistoryRepository;
    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;
    @Autowired
    protected ExchangeRateRepository exchangeRateRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        paymentHistoryRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        subscriptionRepository.deleteAll();
        exchangeRateRepository.deleteAll();
        userRepository.deleteAll();
    }

    protected TokenResponse register(String email) {
        return webTestClient.post()
                .uri(API + "/auth/register")
                .bodyValue(new RegisterRequest(email, "a-long-enough-password"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TokenResponse.class)
                .returnResult()
                .getResponseBody();
    }

    protected TokenResponse login(String email) {
        return webTestClient.post()
                .uri(API + "/auth/login")
                .bodyValue(new LoginRequest(email, "a-long-enough-password"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TokenResponse.class)
                .returnResult()
                .getResponseBody();
    }

    protected String bearer(TokenResponse tokens) {
        return "Bearer " + tokens.accessToken();
    }

    protected User saveAdmin(String email) {
        return userRepository.save(new User(
                email,
                passwordEncoder.encode("a-long-enough-password"),
                Set.of(Role.ADMIN)
        ));
    }

    protected SubscriptionResponse createSubscription(String authorization, SubscriptionRequest request) {
        return webTestClient.post()
                .uri(API + "/subscriptions")
                .header("Authorization", authorization)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(SubscriptionResponse.class)
                .returnResult()
                .getResponseBody();
    }
}
