package com.consoleshop.payment.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the X-User-ID / X-User-Role headers injected by the API Gateway and
 * creates a Spring Security Authentication so controllers can use @PreAuthorize
 * and SecurityContextHolder.getContext().getAuthentication().
 *
 * Requests that do NOT come through the gateway (missing X-Internal-Secret)
 * are rejected unless the path is whitelisted (webhook, actuator).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrustedHeaderAuthFilter extends OncePerRequestFilter {

    @Value("${app.internal-secret}")
    private String internalSecret;

    // Paths that bypass the internal-secret check
    private static final List<String> OPEN_PATHS = List.of(
            "/api/v1/payments/webhook",
            "/actuator"
    );

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Allow open paths without header check
        boolean isOpen = OPEN_PATHS.stream().anyMatch(path::startsWith);

        if (!isOpen) {
            String secret = request.getHeader("X-Internal-Secret");
            log.info("SECRET CHECK path={} expected_len={} received_len={} match={}",
                path, internalSecret.length(),
                secret == null ? 0 : secret.length(),
                internalSecret.equals(secret));
            if (secret == null || !secret.equals(internalSecret)) {
                log.warn("Rejected request to {} — missing or invalid X-Internal-Secret", path);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Forbidden\",\"statusCode\":403}");
                return;
            }
        }

        String userId = request.getHeader("X-User-ID");
        String userRole = request.getHeader("X-User-Role");

        if (userId != null && !userId.isBlank()) {
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + (userRole != null ? userRole : "CUSTOMER")));
            var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
