package com.hacknation.cyfryzacja.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Weryfikuje podpis (HS256), issuer i wygaśnięcie tokenu. Zwraca subject albo pusty wynik. */
@Component
public class JwtTokenVerifier {

    private static final String REQUIRED_ALGORITHM = "HS256";

    private final JwtParser parser;

    public JwtTokenVerifier(JwtProperties properties) {
        this.parser = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8)))
                .requireIssuer(properties.issuer())
                .build();
    }

    public Optional<String> verify(String token) {
        try {
            Jws<Claims> jws = parser.parseSignedClaims(token);
            if (!REQUIRED_ALGORITHM.equals(jws.getHeader().getAlgorithm())) {
                return Optional.empty();
            }
            String subject = jws.getPayload().getSubject();
            return Optional.of(subject == null || subject.isBlank() ? "client" : subject);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
