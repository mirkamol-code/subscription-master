package com.mirkamolcode.service;

import com.mirkamolcode.entity.GmailConnection;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.event.GmailConnectedEvent;
import com.mirkamolcode.repository.GmailConnectionRepository;
import com.mirkamolcode.security.TokenCipher;
import com.mirkamolcode.security.oauth.GoogleProfile;
import com.mirkamolcode.security.oauth.GoogleScopes;

import java.util.Set;
import java.util.TreeSet;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GoogleLoginService {
    private final GoogleUserProvisioner provisioner;
    private final GmailConnectionRepository connections;
    private final TokenCipher cipher;
    private final LoginCodeService loginCodes;
    private final ApplicationEventPublisher events;

    public GoogleLoginService(GoogleUserProvisioner provisioner,
                              GmailConnectionRepository connections,
                              TokenCipher cipher,
                              LoginCodeService loginCodes,
                              ApplicationEventPublisher events) {
        this.provisioner = provisioner;
        this.connections = connections;
        this.cipher = cipher;
        this.loginCodes = loginCodes;
        this.events = events;
    }

    public String completeLogin(GoogleProfile profile, String refreshToken, Set<String> grantedScopes) {
        User user = provisioner.provision(profile);
        if (refreshToken != null && grantedScopes.containsAll(GoogleScopes.GMAIL))
            connectGmail(user, refreshToken, grantedScopes);
        return loginCodes.issue(user);
    }

    private void connectGmail(User user, String refreshToken, Set<String> grantedScopes) {
        String encrypted = cipher.encrypt(refreshToken);
        String scopes = String.join(" ", new TreeSet<>(grantedScopes));
        GmailConnection connection = connections.findByUserId(user.getId())
                .map(existing -> {
                    existing.reconnect(user.getEmail(), encrypted, scopes);
                    return existing;
                })
                .orElseGet(() -> new GmailConnection(user, user.getEmail(), encrypted, scopes));
        connections.save(connection);
        events.publishEvent(new GmailConnectedEvent(user.getId()));
    }
}
