package com.mirkamolcode;

import com.mirkamolcode.dto.AuthDtos.ExchangeRequest;
import com.mirkamolcode.dto.AuthDtos.TokenResponse;
import com.mirkamolcode.dto.request.SubscriptionRequest;
import com.mirkamolcode.detection.Confidence;
import com.mirkamolcode.detection.EmailKind;
import com.mirkamolcode.dto.response.SubscriptionResponse;
import com.mirkamolcode.dto.response.UserProfileResponse;
import com.mirkamolcode.entity.BillingEmail;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.model.Role;
import com.mirkamolcode.repository.BillingEmailRepository;
import com.mirkamolcode.repository.ExchangeRateRepository;
import com.mirkamolcode.repository.GmailConnectionRepository;
import com.mirkamolcode.repository.LoginCodeRepository;
import com.mirkamolcode.repository.PaymentHistoryRepository;
import com.mirkamolcode.repository.RefreshTokenRepository;
import com.mirkamolcode.repository.SubscriptionRepository;
import com.mirkamolcode.repository.UserRepository;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import com.mirkamolcode.service.LoginCodeService;
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
    protected LoginCodeRepository loginCodeRepository;
    @Autowired
    protected BillingEmailRepository billingEmailRepository;
    @Autowired
    protected GmailConnectionRepository gmailConnectionRepository;
    @Autowired
    protected LoginCodeService loginCodeService;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        paymentHistoryRepository.deleteAll();
        billingEmailRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        loginCodeRepository.deleteAll();
        gmailConnectionRepository.deleteAll();
        subscriptionRepository.deleteAll();
        exchangeRateRepository.deleteAll();
        userRepository.deleteAll();
    }

    protected TokenResponse signIn(String email) {
        return signIn(email, Set.of(Role.USER));
    }

    protected TokenResponse signInAsAdmin(String email) {
        return signIn(email, Set.of(Role.ADMIN));
    }

    private TokenResponse signIn(String email, Set<Role> roles) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(new User(email, roles)));
        return webTestClient.post()
                .uri(API + "/auth/exchange")
                .bodyValue(new ExchangeRequest(loginCodeService.issue(user)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TokenResponse.class)
                .returnResult()
                .getResponseBody();
    }

    protected String bearer(TokenResponse tokens) {
        return "Bearer " + tokens.accessToken();
    }

    protected SubscriptionResponse createSubscription(String authorization, SubscriptionRequest request) {
        User user = userRepository.findById(currentUserId(authorization)).orElseThrow();
        Subscription subscription = subscriptionRepository.save(new Subscription(user, request.name(), request.price(),
                request.currency(), request.frequency(), request.status(), request.category(), request.startDate()));
        BillingEmail receipt = BillingEmail.relevant(user, "receipt-" + subscription.getId(), null, EmailKind.RECEIPT,
                request.name() + " <billing@merchant.example>", "Your " + request.name() + " receipt",
                request.startDate().atStartOfDay(ZoneOffset.UTC).toInstant(), Confidence.HIGH, null, null);
        receipt.attachTo(subscription);
        billingEmailRepository.save(receipt);
        return webTestClient.get()
                .uri(API + "/subscriptions/{id}", subscription.getId())
                .header("Authorization", authorization)
                .exchange()
                .expectStatus().isOk()
                .expectBody(SubscriptionResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private Long currentUserId(String authorization) {
        return webTestClient.get()
                .uri(API + "/auth/me")
                .header("Authorization", authorization)
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserProfileResponse.class)
                .returnResult()
                .getResponseBody()
                .id();
    }
}
