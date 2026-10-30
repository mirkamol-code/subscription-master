package com.mirkamolcode.integration.push;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Security;

import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VapidPushSender implements PushSender {
    private static final Logger log = LoggerFactory.getLogger(VapidPushSender.class);

    private final String publicKey;
    private final PushService pushService;

    public VapidPushSender(@Value("${app.push.vapid-public-key}") String publicKey,
                           @Value("${app.push.vapid-private-key}") String privateKey,
                           @Value("${app.push.vapid-subject}") String subject) {
        this.publicKey = publicKey;
        this.pushService = createPushService(publicKey, privateKey, subject);
    }

    @Override
    public String publicKey() {
        return publicKey;
    }

    @Override
    public PushResult send(PushTarget target) {
        if (pushService == null)
            return PushResult.DISABLED;
        try {
            HttpResponse response = pushService.send(new Notification(target.endpoint(), target.p256dh(), target.auth(),
                    target.payload().getBytes(StandardCharsets.UTF_8)), Encoding.AES128GCM);
            int status = response.getStatusLine().getStatusCode();
            if (status == 404 || status == 410)
                return PushResult.EXPIRED;
            return status >= 200 && status < 300 ? PushResult.DELIVERED : PushResult.FAILED;
        } catch (Exception e) {
            log.warn("Web push to {} failed: {}", target.endpoint(), e.getMessage());
            return PushResult.FAILED;
        }
    }

    private static PushService createPushService(String publicKey, String privateKey, String subject) {
        if (publicKey.isBlank() || privateKey.isBlank()) {
            log.info("VAPID keys not configured; web push is disabled");
            return null;
        }
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null)
            Security.addProvider(new BouncyCastleProvider());
        try {
            return new PushService(publicKey, privateKey, subject);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Invalid VAPID keys", e);
        }
    }
}
