package com.mirkamolcode.service;

import com.mirkamolcode.exception.NotFoundException;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.model.Permission;
import com.mirkamolcode.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final UserRepository users;

    public CurrentUserService(UserRepository users) {
        this.users = users;
    }

    public User requiredUser() {
        Authentication auth = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (auth == null || !auth.isAuthenticated())
            throw new NotFoundException("Authenticated user not found");

        return users.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new NotFoundException("Authenticated user not found"));
    }

    public boolean hasPermission(Permission permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals(permission.name()));
    }
}
