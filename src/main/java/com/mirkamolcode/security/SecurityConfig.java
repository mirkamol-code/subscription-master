package com.mirkamolcode.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirkamolcode.dto.ErrorResponse;
import com.mirkamolcode.security.jwt.JwtAuthenticationFilter;
import com.mirkamolcode.security.ratelimit.AuthRateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
                "https://subscriptionmaster.app",
                "https://www.subscriptionmaster.app",
                "https://subscription-master.app",
                "https://www.subscription-master.app",
                "http://localhost:5173",
                "http://127.0.0.1:5173"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin",
                "Access-Control-Request-Method", "Access-Control-Request-Headers"
        ));
        configuration.setExposedHeaders(List.of("Authorization", "Retry-After"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtAuthenticationFilter jwt,
                                            AuthRateLimitFilter rateLimitFilter,
                                            ObjectMapper objectMapper) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(
                                    HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED", "Unauthorized", "Authentication required"));
                        })
                        .accessDeniedHandler((request, response, exception) ->
                                response.setStatus(HttpStatus.FORBIDDEN.value())))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(
                                "/auth/register",
                                "/auth/login",
                                "/auth/refresh",
                                "/auth/logout",
                                "/currencies/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/actuator/health"
                        ).permitAll()
                        .requestMatchers("/auth/me").authenticated()
                        .requestMatchers("/admin/**").hasAuthority("STATISTICS_READ_ALL")
                        .anyRequest().authenticated())
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
