package com.mirkamolcode.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirkamolcode.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final AuthRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public AuthRateLimitFilter(AuthRateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        boolean isAuthEndpoint = "POST".equalsIgnoreCase(method) &&
                (path.endsWith("/auth/login") || path.endsWith("/auth/register"));

        if (!isAuthEndpoint || !rateLimiter.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);

        if (rateLimiter.isBlocked(clientIp)) {
            long remainingSeconds = rateLimiter.getRemainingBlockSeconds(clientIp);
            Instant blockedUntil = rateLimiter.getBlockedUntil(clientIp);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(remainingSeconds));
            response.setHeader("X-RateLimit-Retry-After-Seconds", String.valueOf(remainingSeconds));
            if (blockedUntil != null) {
                response.setHeader("X-RateLimit-Blocked-Until", blockedUntil.toString());
            }
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ErrorResponse error = ErrorResponse.rateLimited(
                    remainingSeconds,
                    blockedUntil,
                    "Too many failed attempts. You are temporarily blocked. Please wait " + remainingSeconds + " seconds before trying again."
            );
            objectMapper.writeValue(response.getOutputStream(), error);
            return;
        }

        StatusExposingResponseWrapper responseWrapper = new StatusExposingResponseWrapper(response);
        try {
            filterChain.doFilter(request, responseWrapper);
            int status = responseWrapper.getStatus();
            if (status >= 200 && status < 300) {
                rateLimiter.recordSuccess(clientIp);
            } else if (status >= 400 && status < 500 && status != HttpStatus.TOO_MANY_REQUESTS.value()) {
                rateLimiter.recordFailure(clientIp);
            }
        } catch (Exception ex) {
            rateLimiter.recordFailure(clientIp);
            throw ex;
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }

    private static class StatusExposingResponseWrapper extends HttpServletResponseWrapper {
        private int httpStatus = HttpServletResponse.SC_OK;

        public StatusExposingResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setStatus(int sc) {
            this.httpStatus = sc;
            super.setStatus(sc);
        }

        @Override
        public void sendError(int sc) throws IOException {
            this.httpStatus = sc;
            super.sendError(sc);
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            this.httpStatus = sc;
            super.sendError(sc, msg);
        }

        @Override
        public int getStatus() {
            return this.httpStatus;
        }
    }
}
