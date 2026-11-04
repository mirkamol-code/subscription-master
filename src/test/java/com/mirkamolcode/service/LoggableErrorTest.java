package com.mirkamolcode.service;

import com.mirkamolcode.exception.ExchangeRatesUnavailableException;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

class LoggableErrorTest {

    @Test
    void describe_shouldWithholdRowValues_whenDatabaseRejectsTheWrite() {
        // given
        String postgresMessage = "ERROR: duplicate key value violates unique constraint \"uk_payment_history_subscription_invoice\"\n"
                + "  Detail: Key (subscription_id, invoice_reference)=(7, 000000000001) already exists. "
                + "Failing row contains (K0000000009, Visa, 4242).";
        RuntimeException failure = new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException(postgresMessage, new SQLException(postgresMessage), "uk_payment_history_subscription_invoice"));

        // when
        String description = LoggableError.describe(failure);

        // then
        assertThat(description)
                .doesNotContain("000000000001")
                .doesNotContain("K0000000009")
                .doesNotContain("4242")
                .doesNotContain("Visa")
                .contains("DataIntegrityViolationException");
    }

    @Test
    void describe_shouldKeepApplicationMessages() {
        // when
        String description = LoggableError.describe(new ExchangeRatesUnavailableException("No exchange rate for EUR"));

        // then
        assertThat(description).isEqualTo("No exchange rate for EUR");
    }
}
