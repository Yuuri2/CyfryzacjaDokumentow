package com.hacknation.cyfryzacja.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Czyta nagłówek "Authorization: Bearer <token>". Poprawny token ustawia uwierzytelnienie;
 * brak lub niepoprawny token zostawia żądanie nieuwierzytelnione (decyzję 401 podejmuje Spring Security).
 * Nie jest beanem – tworzony w SecurityConfig, aby nie był rejestrowany poza łańcuchem filtrów.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenVerifier verifier;

    public JwtAuthFilter(JwtTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            verifier.verify(token).ifPresent(subject ->
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(subject, null, List.of())));
        }
        chain.doFilter(request, response);
    }
}
