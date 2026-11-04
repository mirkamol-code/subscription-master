package com.mirkamolcode.detection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceReferencesTest {

    @Test
    void find_shouldReadInvoiceNumberAndCustomerIdFromHetznerSubject() {
        // when
        InvoiceReferences references = InvoiceReferences.find(
                "Hetzner Online GmbH - Invoice 000000000001 (K0000000001)", "Enclosed you will find your current invoice.");

        // then
        assertThat(references.invoice()).isEqualTo("000000000001");
        assertThat(references.customer()).isEqualTo("K0000000001");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            Invoice no.: INV-2026-0042                      | INV-2026-0042 | C-77
            Invoice number: 2026/0042                       | 2026/0042     | C-77
            Invoice #A1B2C3                                 | A1B2C3        | C-77
            """)
    void find_shouldReadLabelledReferencesFromBody(String invoiceLine, String invoice, String customer) {
        // when
        InvoiceReferences references = InvoiceReferences.find("Your invoice", invoiceLine + "\nCustomer number: C-77");

        // then
        assertThat(references.invoice()).isEqualTo(invoice);
        assertThat(references.customer()).isEqualTo(customer);
    }

    @Test
    void find_shouldReturnNothing_whenEmailHasNoReferences() {
        // when
        InvoiceReferences references = InvoiceReferences.find("Your receipt from Acme", "Thanks for your payment of $5.00.");

        // then
        assertThat(references.invoice()).isNull();
        assertThat(references.customer()).isNull();
    }
}
