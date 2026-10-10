package com.mirkamolcode.security.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuthRateLimitFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void doFilter_whenBlocked_shouldReturn429WithAuthLockedErrorCode() throws Exception {
        AuthRateLimiter limiter = new AuthRateLimiter(true, 3, 60);
        String ip = "192.168.1.100";
        // 3 failures to trigger block
        limiter.recordFailure(ip);
        limiter.recordFailure(ip);
        limiter.recordFailure(ip);

        AuthRateLimitFilter filter = new AuthRateLimitFilter(limiter, objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isNotNull();

        JsonNode json = objectMapper.readTree(response.getContentAsString());
        assertThat(json.get("errorCode").asText()).isEqualTo("AUTH_LOCKED");
        assertThat(json.get("retryAfterSeconds").asLong()).isGreaterThan(0);
        assertThat(json.get("status").asInt()).isEqualTo(429);
    }
}
