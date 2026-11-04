package com.mirkamolcode.detection;

import com.mirkamolcode.model.PaymentMethodType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMethodParserTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', nullValues = "-", textBlock = """
            Visa •••• 4242 US$3,99                         | Visa       | 4242
            Paid with Visa ending in 4242                  | Visa       | 4242
            Mastercard ****4242                            | Mastercard | 4242
            AMEX ···· 1005                                 | Amex       | 1005
            American Express ending in 1005                | Amex       | 1005
            Charged to card *4242                          | -          | 4242
            Card •••• 4242                                 | -          | 4242
            Humo •••• 9860                                 | Humo       | 9860
            """)
    void find_shouldReadCardBrandAndLastFourDigits(String text, String brand, String lastFour) {
        // when
        PaymentMethod method = PaymentMethodParser.find(text).orElseThrow();

        // then
        assertThat(method.type()).isEqualTo(PaymentMethodType.CARD);
        assertThat(method.cardBrand()).isEqualTo(brand);
        assertThat(method.cardLast4()).isEqualTo(lastFour);
    }

    @Test
    void find_shouldReturnCardWithoutDetails_whenEmailOnlySaysCreditCard() {
        // when
        PaymentMethod method = PaymentMethodParser.find(
                "The open invoice amount of $ 10.09 will soon be debited from your credit card.").orElseThrow();

        // then
        assertThat(method.type()).isEqualTo(PaymentMethodType.CARD);
        assertThat(method.cardBrand()).isNull();
        assertThat(method.cardLast4()).isNull();
    }

    @Test
    void find_shouldReadPaypal() {
        // when
        PaymentMethod method = PaymentMethodParser.find("Payment method: PayPal (alex.doe@example.com)").orElseThrow();

        // then
        assertThat(method.type()).isEqualTo(PaymentMethodType.PAYPAL);
        assertThat(method.cardLast4()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            Card number 4111 1111 1111 1111 was charged
            Card 4111111111111111 was charged
            Visa 4111-1111-1111-1111
            """)
    void find_shouldNeverReadDigitsFromFullCardNumber(String text) {
        // when / then
        assertThat(PaymentMethodParser.find(text).map(PaymentMethod::cardLast4)).isEmpty();
    }

    @Test
    void find_shouldReturnEmpty_whenNoPaymentMethodIsMentioned() {
        // when / then
        assertThat(PaymentMethodParser.find("Order ID: MT0000TEST Document: 100000000001")).isEmpty();
    }
}
