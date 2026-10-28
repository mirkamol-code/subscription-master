package com.mirkamolcode.security.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
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

    @Test
    void doFilter_shouldIgnoreClientSuppliedForwardedHeaders_whenCountingFailures() throws Exception {
        // given every failed login claims a different X-Forwarded-For address
        AuthRateLimiter limiter = new AuthRateLimiter(true, 3, 60);
        AuthRateLimitFilter filter = new AuthRateLimitFilter(limiter, objectMapper);
        FilterChain rejectingChain = (req, res) -> ((HttpServletResponse) res).setStatus(401);
        for (int i = 1; i <= 3; i++) {
            filter.doFilter(loginFrom("203.0.113.7", "10.0.0." + i), new MockHttpServletResponse(), rejectingChain);
        }

        // when the same client tries again with yet another spoofed address
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(loginFrom("203.0.113.7", "10.0.0.99"), response, rejectingChain);

        // then
        assertThat(response.getStatus()).isEqualTo(429);
    }

    private MockHttpServletRequest loginFrom(String remoteAddr, String spoofedForwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr(remoteAddr);
        request.addHeader("X-Forwarded-For", spoofedForwardedFor);
        request.addHeader("X-Real-IP", spoofedForwardedFor);
        return request;
    }
}
