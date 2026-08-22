package com.mirkamolcode.service;

import com.mirkamolcode.entity.User;
import com.mirkamolcode.exception.NotFoundException;
import com.mirkamolcode.model.Permission;
import com.mirkamolcode.model.Role;
import com.mirkamolcode.repository.UserRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private CurrentUserService underTest;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requiredUser_shouldReturnUser_whenAuthenticationAndDatabaseUserExist() {
        // Given
        User user = new User("person@example.com", "hash", Set.of(Role.USER));
        authenticate("person@example.com", Permission.SUBSCRIPTION_READ_OWN);
        when(userRepository.findByEmailIgnoreCase("person@example.com")).thenReturn(Optional.of(user));

        // When
        User result = underTest.requiredUser();

        // Then
        assertThat(result).isSameAs(user);
    }

    @Test
    void requiredUser_shouldThrowNotFound_whenAuthenticationIsMissingOrUserDoesNotExist() {
        assertThatThrownBy(() -> underTest.requiredUser())
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Authenticated user not found");

        authenticate("missing@example.com", Permission.SUBSCRIPTION_READ_OWN);
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> underTest.requiredUser())
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Authenticated user not found");
    }

    @Test
    void hasPermission_shouldReflectCurrentAuthorities() {
        // Given
        authenticate("person@example.com", Permission.SUBSCRIPTION_READ_OWN);

        // When / Then
        assertThat(underTest.hasPermission(Permission.SUBSCRIPTION_READ_OWN)).isTrue();
        assertThat(underTest.hasPermission(Permission.SUBSCRIPTION_READ_ALL)).isFalse();

        SecurityContextHolder.clearContext();
        assertThat(underTest.hasPermission(Permission.SUBSCRIPTION_READ_OWN)).isFalse();
    }

    private void authenticate(String email, Permission permission) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        email,
                        null,
                        Set.of(new SimpleGrantedAuthority(permission.name()))
                )
        );
        SecurityContextHolder.setContext(context);
    }
}
