package com.mirkamolcode.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirkamolcode.dto.ErrorResponse;
import com.mirkamolcode.security.jwt.JwtAuthenticationFilter;
import com.mirkamolcode.security.oauth.GoogleLoginFailureHandler;
import com.mirkamolcode.security.oauth.GoogleLoginSuccessHandler;
import com.mirkamolcode.security.oauth.HttpCookieOAuth2AuthorizationRequestRepository;
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
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
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
        configuration.setExposedHeaders(List.of("Authorization", "Retry-After", "Content-Disposition", "X-Blocked-Remote-Images"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtAuthenticationFilter jwt,
                                            ObjectMapper objectMapper,
                                            ClientRegistrationRepository clientRegistrations,
                                            HttpCookieOAuth2AuthorizationRequestRepository authorizationRequests,
                                            GoogleLoginSuccessHandler loginSuccessHandler,
                                            GoogleLoginFailureHandler loginFailureHandler) throws Exception {
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
                                "/oauth2/authorization/**",
                                "/login/oauth2/code/**",
                                "/auth/exchange",
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
                .oauth2Login(o -> o
                        .loginPage("/oauth2/authorization/google")
                        .authorizationEndpoint(a -> a
                                .authorizationRequestRepository(authorizationRequests)
                                .authorizationRequestResolver(offlineConsentResolver(clientRegistrations)))
                        .successHandler(loginSuccessHandler)
                        .failureHandler(loginFailureHandler))
                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private static OAuth2AuthorizationRequestResolver offlineConsentResolver(ClientRegistrationRepository clientRegistrations) {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(clientRegistrations, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(request -> request.additionalParameters(parameters -> {
            parameters.put("access_type", "offline");
            parameters.put("prompt", "consent");
        }));
        return resolver;
    }
}
