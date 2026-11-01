package com.mirkamolcode.security;

import com.mirkamolcode.model.Role;
import com.mirkamolcode.repository.UserRepository;

import java.util.function.Supplier;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

@Component
public class AdminAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
    private final UserRepository users;

    public AdminAuthorizationManager(UserRepository users) {
        this.users = users;
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        return new AuthorizationDecision(isAdmin(authentication.get()));
    }

    public boolean isAdmin(Authentication authentication) {
        boolean signedIn = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        return signedIn && users.hasRole(authentication.getName(), Role.ADMIN);
    }
}
