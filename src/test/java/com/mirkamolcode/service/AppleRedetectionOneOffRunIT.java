package com.mirkamolcode.service;

import com.mirkamolcode.SharedPostgresContainer;
import com.mirkamolcode.SubscribeMasterApplication;
import com.mirkamolcode.detection.EmailFixtures;
import com.mirkamolcode.integration.gmail.GmailClient;
import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.integration.google.GoogleOAuthClient;
import com.mirkamolcode.security.TokenCipher;
import com.mirkamolcode.service.AppleRedetectionReport.Outcome;
import com.mirkamolcode.service.AppleRedetectionReport.UserPlan;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

class AppleRedetectionOneOffRunIT {
    private static final String DATABASE = "apple_dry_run_v10";
    private static final String TEST_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final List<String> TABLES = List.of("users", "gmail_connections", "subscriptions", "payment_history",
            "billing_emails", "status_changes", "notifications", "exchange_rates", "failed_gmail_messages", "merchant_tombstones");
    private static final FakeGmail GMAIL = new FakeGmail();
    private static final GmailMessage TELEGRAM = EmailFixtures.load("apple-telegram-invoice.json");
    private static final GmailMessage MULTI = EmailFixtures.load("apple-multi-item-invoice.json");

    private static String url;
    private static JdbcTemplate jdbc;
    private static long userId;

    @TestConfiguration
    static class FakeGoogle {
        @Bean
        @Primary
        GmailClient fakeGmailClient() {
            return GMAIL;
        }

        @Bean
        @Primary
        GoogleOAuthClient fakeGoogleOAuthClient() {
            return new FakeGoogleOAuth();
        }
    }

    @BeforeAll
    static void createDatabaseAtProductionSchema() {
        SharedPostgresContainer postgres = SharedPostgresContainer.running();
        JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        admin.execute("DROP DATABASE IF EXISTS " + DATABASE);
        admin.execute("CREATE DATABASE " + DATABASE);
        url = postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + DATABASE);
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(dataSource).target("10").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        seedLegacyAppleRows();
        GMAIL.reset();
        GMAIL.mailbox(MULTI, TELEGRAM);
    }

    @Test
    void oneOffRun_shouldReportFromProductionSchema_withoutSchedulingMigratingOrWriting() {
        // given
        Map<String, Integer> before = countRows();

        // when
        UserPlan plan;
        boolean schedulingEnabled;
        try (ConfigurableApplicationContext context = startOneOffRun()) {
            schedulingEnabled = context.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class).length > 0;
            plan = context.getBean(AppleRedetectionDryRun.class).run().planFor(userId);
        }

        // then
        assertThat(schedulingEnabled).isFalse();
        assertThat(plan.outcome()).isEqualTo(Outcome.READY);
        assertThat(plan.services()).extracting(AppleRedetectionReport.PredictedService::name)
                .containsExactlyInAnyOrder("Telegram Messenger", "iCloud+");
        assertThat(plan.paymentsRebuilt()).isEqualTo(3);
        assertThat(countRows()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select max(version::int) from flyway_schema_history where success", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select status from gmail_connections where user_id = ?", String.class, userId)).isEqualTo("CONNECTED");
    }

    private static ConfigurableApplicationContext startOneOffRun() {
        SharedPostgresContainer postgres = SharedPostgresContainer.running();
        ConfigurableApplicationContext context = new SpringApplicationBuilder(SubscribeMasterApplication.class, FakeGoogle.class)
                .profiles("test", "apple-dry-run")
                .run("--spring.datasource.url=" + url,
                        "--spring.datasource.username=" + postgres.getUsername(),
                        "--spring.datasource.password=" + postgres.getPassword(),
                        "--app.redetection.apple.exit-after-dry-run=false");
        String connectedTo = context.getEnvironment().getProperty("spring.datasource.url");
        if (connectedTo == null || !connectedTo.contains("/" + DATABASE)) {
            context.close();
            throw new IllegalStateException("One-off run must use the throwaway database, not " + connectedTo);
        }
        return context;
    }

    private static void seedLegacyAppleRows() {
        Timestamp now = Timestamp.from(Instant.parse("2026-11-01T00:00:00Z"));
        userId = jdbc.queryForObject("""
                insert into users (email, version, created_at, updated_at, time_zone) values ('legacy@example.com', 0, ?, ?, 'Asia/Tashkent')
                returning id""", Long.class, now, now);
        jdbc.update("""
                insert into user_roles (user_id, role) select ?, 'USER' where exists
                    (select 1 from information_schema.tables where table_name = 'user_roles')""", userId);
        jdbc.update("""
                insert into gmail_connections (user_id, gmail_address, encrypted_refresh_token, granted_scopes, status, version, created_at, updated_at)
                values (?, 'legacy@example.com', ?, 'gmail.readonly gmail.send', 'CONNECTED', 0, ?, ?)""",
                userId, new TokenCipher(TEST_KEY).encrypt("1//legacy"), now, now);
        long subscriptionId = jdbc.queryForObject("""
                insert into subscriptions (user_id, name, price, currency, frequency, status, category, start_date, next_payment_date,
                    is_deleted, merchant_key, needs_review, is_hidden, logo_domain, version, created_at, updated_at)
                values (?, 'Apple (App Store / iCloud)', 0.43, 'USD', 'MONTHLY', 'ACTIVE', 'OTHER', '2025-12-06', '2026-01-06',
                    false, 'apple', false, false, 'apple.com', 0, ?, ?) returning id""", Long.class, userId, now, now);
        for (GmailMessage message : List.of(MULTI, TELEGRAM)) {
            long emailId = jdbc.queryForObject("""
                    insert into billing_emails (user_id, subscription_id, gmail_message_id, thread_id, kind, sender, subject, received_at,
                        confidence, merchant_key, created_at, updated_at)
                    values (?, ?, ?, ?, 'RECEIPT', 'Apple <no_reply@email.apple.com>', 'Your invoice from Apple.', ?, 'LOW', 'apple', ?, ?)
                    returning id""", Long.class, userId, subscriptionId, message.id(), message.threadId(),
                    Timestamp.from(message.receivedAt()), now, now);
            jdbc.update("""
                    insert into payment_history (subscription_id, billing_email_id, payment_date, original_amount, original_currency,
                        exchange_rate_to_uzs, amount_uzs, created_at, updated_at)
                    values (?, ?, '2025-12-07', 3.99, 'USD', 12850, 51271.5, ?, ?)""", subscriptionId, emailId, now, now);
        }
    }

    private static Map<String, Integer> countRows() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String table : TABLES)
            counts.put(table, jdbc.queryForObject("select count(*) from " + table, Integer.class));
        counts.put("subscriptions_changed_since_seed", jdbc.queryForObject(
                "select count(*) from subscriptions where updated_at > '2026-11-01'", Integer.class));
        return counts;
    }
}
