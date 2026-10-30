package com.mirkamolcode.integration.push;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class VapidPushSenderTest {
    private static final String BROWSER_P256DH = "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM";
    private static final String BROWSER_AUTH = "tBHItJI5svbpez7KI4CCXg";

    private WireMockServer pushService;
    private VapidPushSender underTest;

    @BeforeEach
    void setUp() throws Exception {
        pushService = new WireMockServer(wireMockConfig().dynamicPort());
        pushService.start();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair vapid = generator.generateKeyPair();
        underTest = new VapidPushSender(encodePublicKey((ECPublicKey) vapid.getPublic()),
                encodePrivateKey((ECPrivateKey) vapid.getPrivate()), "mailto:admin@example.com");
    }

    @AfterEach
    void tearDown() {
        pushService.stop();
    }

    @Test
    void send_shouldPostEncryptedPayloadWithVapidAuthorization() {
        // given
        pushService.stubFor(post("/push/abc").willReturn(aResponse().withStatus(201)));

        // when
        PushResult delivery = underTest.send(new PushTarget(pushService.baseUrl() + "/push/abc", BROWSER_P256DH, BROWSER_AUTH,
                "{\"title\":\"Netflix renews on 13 Nov 2026\"}"));

        // then
        assertThat(delivery).isEqualTo(PushResult.DELIVERED);
        pushService.verify(postRequestedFor(urlEqualTo("/push/abc"))
                .withHeader("Authorization", matching("vapid t=.+, k=.+"))
                .withHeader("Content-Encoding", equalTo("aes128gcm"))
                .withHeader("TTL", matching("\\d+"))
                .withRequestBody(notContaining("Netflix")));
    }

    @Test
    void send_shouldReportExpired_whenPushServiceSaysSubscriptionIsGone() {
        // given
        pushService.stubFor(post("/push/gone").willReturn(aResponse().withStatus(410)));

        // when / then
        assertThat(underTest.send(new PushTarget(pushService.baseUrl() + "/push/gone", BROWSER_P256DH, BROWSER_AUTH, "{}")))
                .isEqualTo(PushResult.EXPIRED);
    }

    @Test
    void send_shouldBeDisabled_whenVapidKeysAreNotConfigured() {
        // given
        VapidPushSender unconfigured = new VapidPushSender("", "", "mailto:admin@example.com");

        // when / then
        assertThat(unconfigured.send(new PushTarget("https://fcm.googleapis.com/x", BROWSER_P256DH, BROWSER_AUTH, "{}")))
                .isEqualTo(PushResult.DISABLED);
    }

    private static String encodePublicKey(ECPublicKey key) {
        byte[] point = new byte[65];
        point[0] = 0x04;
        copyUnsigned(key.getW().getAffineX(), point, 1);
        copyUnsigned(key.getW().getAffineY(), point, 33);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(point);
    }

    private static String encodePrivateKey(ECPrivateKey key) {
        byte[] scalar = new byte[32];
        copyUnsigned(key.getS(), scalar, 0);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(scalar);
    }

    private static void copyUnsigned(BigInteger value, byte[] target, int offset) {
        byte[] bytes = value.toByteArray();
        byte[] trimmed = bytes.length > 32 ? Arrays.copyOfRange(bytes, bytes.length - 32, bytes.length) : bytes;
        System.arraycopy(trimmed, 0, target, offset + 32 - trimmed.length, trimmed.length);
    }
}
