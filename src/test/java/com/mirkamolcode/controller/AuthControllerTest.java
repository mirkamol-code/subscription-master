package com.mirkamolcode.controller;

import com.mirkamolcode.dto.AuthDtos.LogoutRequest;
import com.mirkamolcode.dto.response.UserProfileResponse;
import com.mirkamolcode.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController underTest;

    @Test
    void me_shouldReturnUserProfile() {
        // Given
        Instant createdAt = Instant.parse("2026-01-15T10:00:00Z");
        UserProfileResponse response = new UserProfileResponse(
                104L,
                "user@example.com",
                List.of("ROLE_USER"),
                "UZS",
                createdAt
        );
        when(authService.me()).thenReturn(response);

        // When
        UserProfileResponse result = underTest.me();

        // Then
        assertThat(result.id()).isEqualTo(104L);
        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(result.roles()).containsExactly("ROLE_USER");
        assertThat(result.baseCurrency()).isEqualTo("UZS");
        assertThat(result.createdAt()).isEqualTo(createdAt);
        verify(authService).me();
    }

    @Test
    void logout_shouldInvokeAuthServiceLogout() {
        // Given
        LogoutRequest request = new LogoutRequest("sample-refresh-token");

        // When
        underTest.logout(request);

        // Then
        verify(authService).logout(request);
    }
}
