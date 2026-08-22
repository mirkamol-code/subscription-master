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
        String authorization = bearer(register("owner@example.com"));

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
    void create_shouldReturnBadRequest_whenPayloadViolatesValidation() {
        String authorization = bearer(register("owner@example.com"));
        SubscriptionRequest invalid = new SubscriptionRequest("", BigDecimal.ZERO, null, null, null, null, null);

        webTestClient.post()
                .uri(API + "/subscriptions")
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
        String ownerAuthorization = bearer(register("owner@example.com"));
        String otherAuthorization = bearer(register("other@example.com"));
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
    void admin_shouldListReadUpdateAndDeleteSubscriptionsAcrossUsers() {
        String ownerAuthorization = bearer(register("owner@example.com"));
        SubscriptionResponse subscription = createSubscription(
                ownerAuthorization, request("Owner service", "10.00", CurrencyCode.UZS));
        saveAdmin("admin@example.com");
        String adminAuthorization = bearer(login("admin@example.com"));

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
        String ownerAuthorization = bearer(register("owner@example.com"));
        String otherAuthorization = bearer(register("other@example.com"));
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

    private SubscriptionRequest request(String name, String price, CurrencyCode currency) {
        return new SubscriptionRequest(name, new BigDecimal(price), currency,
                BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE,
                SubscriptionCategory.ENTERTAINMENT, LocalDate.of(2026, 1, 10));
    }
}
