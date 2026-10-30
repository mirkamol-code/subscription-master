package com.mirkamolcode.service;

import com.mirkamolcode.entity.User;
import com.mirkamolcode.exception.UnauthorizedException;
import com.mirkamolcode.model.Role;
import com.mirkamolcode.repository.UserRepository;
import com.mirkamolcode.security.oauth.GoogleProfile;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GoogleUserProvisioner {
    private static final Logger log = LoggerFactory.getLogger(GoogleUserProvisioner.class);

    private final UserRepository users;
    private final Set<String> adminEmails;

    public GoogleUserProvisioner(UserRepository users,
                                 @Value("${app.security.admin-emails:}") List<String> adminEmails) {
        this.users = users;
        this.adminEmails = adminEmails.stream()
                .map(GoogleUserProvisioner::normalize)
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public User provision(GoogleProfile profile) {
        if (!profile.emailVerified())
            throw new UnauthorizedException("Google account email is not verified");

        String email = normalize(profile.email());
        User user = users.findByGoogleSubject(profile.subject())
                .or(() -> users.findByEmailIgnoreCase(email))
                .orElseGet(() -> new User(email, Set.of(Role.USER)));

        if (isLinkedToAnotherGoogleAccount(user, profile))
            throw new UnauthorizedException("This email is already linked to another Google account");

        user.linkGoogleAccount(profile.subject(), chooseEmail(user, email), profile.displayName(), profile.avatarUrl());
        syncRoles(user);
        return users.save(user);
    }

    private String chooseEmail(User user, String googleEmail) {
        if (user.getEmail() == null || user.getEmail().equals(googleEmail))
            return googleEmail;
        boolean takenByAnotherUser = users.findByEmailIgnoreCase(googleEmail)
                .filter(other -> !other.getId().equals(user.getId()))
                .isPresent();
        if (!takenByAnotherUser)
            return googleEmail;
        log.warn("Google email of user {} changed to an address another account uses; keeping the stored email", user.getId());
        return user.getEmail();
    }

    private void syncRoles(User user) {
        user.grantRole(Role.USER);
        if (adminEmails.contains(user.getEmail()))
            user.grantRole(Role.ADMIN);
        else
            user.revokeRole(Role.ADMIN);
    }

    private static boolean isLinkedToAnotherGoogleAccount(User user, GoogleProfile profile) {
        return user.getGoogleSubject() != null && !user.getGoogleSubject().equals(profile.subject());
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
