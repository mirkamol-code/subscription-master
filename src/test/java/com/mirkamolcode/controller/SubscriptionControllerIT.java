package com.mirkamolcode.controller;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.dto.request.SubscriptionRequest;
import com.mirkamolcode.dto.response.SubscriptionResponse;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionControllerIT extends AbstractTestConfig {

    @Test
    void endpoints_shouldRejectMissingAuthentication() {
        webTestClient.get()
                .uri(API + "/subscriptions")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void crud_shouldCreateReadUpdateAndSoftDeleteOwnedSubscription() {
        String authorization = bearer(signIn("owner@example.com"));

        SubscriptionResponse created = createSubscription(authorization, request("Netflix", "12.99", CurrencyCode.USD));

        webTestClient.get()
                .uri(API + "/subscriptions/{id}", created.id())
                .header("Authorization", authorization)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Netflix");

        webTestClient.put()
                .uri(API + "/subscriptions/{id}", created.id())
                .header("Authorization", authorization)
                .bodyValue(request("Netflix Premium", "15.99", CurrencyCode.USD))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Netflix Premium")
                .jsonPath("$.price").isEqualTo(15.99);

        webTestClient.delete()
                .uri(API + "/subscriptions/{id}", created.id())
                .header("Authorization", authorization)
                .exchange()
                .expectStatus().isNoContent();

        webTestClient.get()
                .uri(API + "/subscriptions/{id}", created.id())
                .header("Authorization", authorization)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void create_shouldNotBeOffered_becauseSubscriptionsComeOnlyFromEmails() {
        String authorization = bearer(signIn("owner@example.com"));

        webTestClient.post()
                .uri(API + "/subscriptions")
                .header("Authorization", authorization)
                .bodyValue(request("YouTube Premium", "13.99", CurrencyCode.USD))
                .exchange()
                .expectStatus().isEqualTo(405);

        assertThat(subscriptionRepository.findAll()).isEmpty();
    }

    @Test
    void update_shouldReturnBadRequest_whenPayloadViolatesValidation() {
        String authorization = bearer(signIn("owner@example.com"));
        SubscriptionResponse owned = createSubscription(authorization, request("Netflix", "12.99", CurrencyCode.USD));
        SubscriptionRequest invalid = new SubscriptionRequest("", BigDecimal.ZERO, null, null, null, null, null);

        webTestClient.put()
                .uri(API + "/subscriptions/{id}", owned.id())
                .header("Authorization", authorization)
                .bodyValue(invalid)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.fields.name").exists()
                .jsonPath("$.fields.price").exists();
    }

    @Test
    void user_shouldNotReadUpdateOrDeleteAnotherUsersSubscription() {
        String ownerAuthorization = bearer(signIn("owner@example.com"));
        String otherAuthorization = bearer(signIn("other@example.com"));
        SubscriptionResponse privateSubscription = createSubscription(
                otherAuthorization, request("Private", "9.99", CurrencyCode.UZS));

        webTestClient.get()
                .uri(API + "/subscriptions/{id}", privateSubscription.id())
                .header("Authorization", ownerAuthorization)
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.put()
                .uri(API + "/subscriptions/{id}", privateSubscription.id())
                .header("Authorization", ownerAuthorization)
                .bodyValue(request("Stolen", "1.00", CurrencyCode.UZS))
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.delete()
                .uri(API + "/subscriptions/{id}", privateSubscription.id())
                .header("Authorization", ownerAuthorization)
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path(API + "/subscriptions").build())
                .header("Authorization", ownerAuthorization)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(0);
    }

    @Test
    void user_shouldNotHideUnhideOrReadHistoryOfAnotherUsersSubscription() {
        String ownerAuthorization = bearer(signIn("owner-actions@example.com"));
        String otherAuthorization = bearer(signIn("other-actions@example.com"));
        SubscriptionResponse ownerSubscription = createSubscription(
                ownerAuthorization, request("Owner only", "9.99", CurrencyCode.USD));

        webTestClient.post()
                .uri(API + "/subscriptions/{id}/hide", ownerSubscription.id())
                .header("Authorization", otherAuthorization)
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.post()
                .uri(API + "/subscriptions/{id}/unhide", ownerSubscription.id())
                .header("Authorization", otherAuthorization)
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.get()
                .uri(API + "/subscriptions/{id}/history", ownerSubscription.id())
                .header("Authorization", otherAuthorization)
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.get()
                .uri(API + "/subscriptions/all")
                .header("Authorization", otherAuthorization)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(0);

        webTestClient.get()
                .uri(API + "/subscriptions/{id}", ownerSubscription.id())
                .header("Authorization", ownerAuthorization)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ACTIVE");
    }

    @Test
    void admin_shouldListReadUpdateAndDeleteSubscriptionsAcrossUsers() {
        String ownerAuthorization = bearer(signIn("owner@example.com"));
        SubscriptionResponse subscription = createSubscription(
                ownerAuthorization, request("Owner service", "10.00", CurrencyCode.UZS));
        String adminAuthorization = bearer(signInAsAdmin("admin@example.com"));

        webTestClient.get()
                .uri(API + "/subscriptions")
                .header("Authorization", adminAuthorization)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1);

        webTestClient.get()
                .uri(API + "/subscriptions/{id}", subscription.id())
                .header("Authorization", adminAuthorization)
                .exchange()
                .expectStatus().isOk();

        webTestClient.put()
                .uri(API + "/subscriptions/{id}", subscription.id())
                .header("Authorization", adminAuthorization)
                .bodyValue(request("Admin updated", "20.00", CurrencyCode.UZS))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Admin updated");

        webTestClient.delete()
                .uri(API + "/subscriptions/{id}", subscription.id())
                .header("Authorization", adminAuthorization)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void list_shouldApplyFiltersPaginationAndOwnerIsolation() {
        String ownerAuthorization = bearer(signIn("owner@example.com"));
        String otherAuthorization = bearer(signIn("other@example.com"));
        createSubscription(ownerAuthorization, request("Zeta", "30.00", CurrencyCode.USD));
        createSubscription(ownerAuthorization, request("Alpha", "5.00", CurrencyCode.USD));
        createSubscription(otherAuthorization, request("Other", "50.00", CurrencyCode.USD));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path(API + "/subscriptions")
                        .queryParam("currency", "USD")
                        .queryParam("minPrice", "10")
                        .queryParam("size", "1")
                        .queryParam("sort", "name,asc")
                        .build())
                .header("Authorization", ownerAuthorization)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].name").isEqualTo("Zeta");
    }

    @Test
    void list_shouldSupportCaseInsensitiveEnumsAndCustomSortSyntax() {
        String ownerAuthorization = bearer(signIn("owner-enums@example.com"));
        createSubscription(ownerAuthorization, request("Service A", "10.00", CurrencyCode.USD));
        createSubscription(ownerAuthorization, request("Service B", "20.00", CurrencyCode.USD));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path(API + "/subscriptions")
                        .queryParam("status", "active")
                        .queryParam("currency", "usd")
                        .queryParam("sort", "name,ASC")
                        .build())
                .header("Authorization", ownerAuthorization)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.content[0].name").isEqualTo("Service A");
    }

    @Test
    void listAll_shouldTreatPercentAndUnderscoreInNameAsLiteralCharacters() {
        String ownerAuthorization = bearer(signIn("owner-wildcards@example.com"));
        createSubscription(ownerAuthorization, request("100% Fitness", "10.00", CurrencyCode.USD));
        createSubscription(ownerAuthorization, request("My_Box", "10.00", CurrencyCode.USD));
        createSubscription(ownerAuthorization, request("MyXBox", "10.00", CurrencyCode.USD));
        createSubscription(ownerAuthorization, request("Netflix", "10.00", CurrencyCode.USD));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path(API + "/subscriptions/all")
                        .queryParam("name", "{name}")
                        .build("%"))
                .header("Authorization", ownerAuthorization)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].name").isEqualTo("100% Fitness");

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path(API + "/subscriptions/all")
                        .queryParam("name", "{name}")
                        .build("y_b"))
                .header("Authorization", ownerAuthorization)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].name").isEqualTo("My_Box");
    }

    @Test
    void calendar_shouldReturnBadRequest_whenYearIsOutsideSupportedRange() {
        String authorization = bearer(signIn("owner-calendar@example.com"));

        webTestClient.get()
                .uri(API + "/subscriptions/calendar?year=1999&month=10")
                .header("Authorization", authorization)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("BAD_REQUEST");
    }

    @Test
    void update_shouldRejectAStaleVersion_andKeepTheNewerEdit() {
        String authorization = bearer(signIn("editor@example.com"));
        SubscriptionResponse created = createSubscription(authorization, request("Netflix", "10.00", CurrencyCode.USD));

        // two edits in a row, each sending the version the previous response returned
        SubscriptionResponse firstEdit = update(authorization, created.id(), withVersion(request("Netflix Premium", "15.99", CurrencyCode.USD), created.version()))
                .expectStatus().isOk()
                .expectBody(SubscriptionResponse.class)
                .returnResult()
                .getResponseBody();
        update(authorization, created.id(), withVersion(request("Netflix Family", "22.99", CurrencyCode.USD), firstEdit.version()))
                .expectStatus().isOk();

        // an edit made from the original, now stale, copy
        update(authorization, created.id(), withVersion(request("Stale edit", "1.00", CurrencyCode.USD), created.version()))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("CONFLICT");

        webTestClient.get()
                .uri(API + "/subscriptions/{id}", created.id())
                .header("Authorization", authorization)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Netflix Family");
    }

    private WebTestClient.ResponseSpec update(String authorization, Long id, SubscriptionRequest request) {
        return webTestClient.put()
                .uri(API + "/subscriptions/{id}", id)
                .header("Authorization", authorization)
                .bodyValue(request)
                .exchange();
    }

    private SubscriptionRequest withVersion(SubscriptionRequest request, long version) {
        return new SubscriptionRequest(request.name(), request.price(), request.currency(), request.frequency(),
                request.status(), request.category(), request.startDate(), version);
    }

    private SubscriptionRequest request(String name, String price, CurrencyCode currency) {
        return new SubscriptionRequest(name, new BigDecimal(price), currency,
                BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE,
                SubscriptionCategory.ENTERTAINMENT, LocalDate.of(2026, 1, 10));
    }
}
