package com.consoleshop.payment.config;

import com.consoleshop.payment.security.TrustedHeaderAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final TrustedHeaderAuthFilter trustedHeaderAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Webhook and health endpoints are open
                .requestMatchers("/api/v1/payments/webhook", "/actuator/**", "/actuator/health").permitAll()
                // Everything else requires authentication (enforced via TrustedHeaderAuthFilter)
                .anyRequest().authenticated()
            )
            .addFilterBefore(trustedHeaderAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
